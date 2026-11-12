package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.tour.dto.request.CreateTourCodeRequest;
import com.trekking.app.features.tour.dto.request.DeleteTourCodeRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourCodeRequest;
import com.trekking.app.features.tour.dto.response.TourCodeLogResponse;
import com.trekking.app.features.tour.dto.response.TourCodeResponse;
import com.trekking.app.features.tour.entity.TourCode;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.TourCodeRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.service.TourCodeService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class TourCodeServiceImpl implements TourCodeService {
    private static final String ENTITY_TYPE = "tour_code";

    private final TourCodeRepository codes;
    private final TourRepository tours;
    private final AuthorizationService authorization;
    private final AuditLogRepository audits;
    private final ObjectMapper json;
    private final Clock clock;
    private final EntityManager entities;

    public TourCodeServiceImpl(TourCodeRepository codes, TourRepository tours, AuthorizationService authorization,
                               AuditLogRepository audits, ObjectMapper json, Clock clock, EntityManager entities) {
        this.codes = codes;
        this.tours = tours;
        this.authorization = authorization;
        this.audits = audits;
        this.json = json;
        this.clock = clock;
        this.entities = entities;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TourCodeResponse> list(Long tourId, boolean includeDeleted, String keyword, Pageable pageable) {
        authorization.require(Permission.TOUR_READ);
        String code = "";
        if (keyword != null && !keyword.isBlank()) {
            code = normalize(keyword);
        }

        Page<TourCode> page;
        if (tourId != null && includeDeleted) {
            page = codes.findAllByTourIdAndCodeContaining(tourId, code, pageable);
        } else if (tourId != null) {
            page = codes.findAllByTourIdAndDeletedAtIsNullAndCodeContaining(tourId, code, pageable);
        } else if (includeDeleted) {
            page = codes.findAllByCodeContaining(code, pageable);
        } else {
            page = codes.findAllByDeletedAtIsNullAndCodeContaining(code, pageable);
        }
        return page.map(TourCodeServiceImpl::response);
    }

    @Override
    @Transactional(readOnly = true)
    public TourCodeResponse get(long id) {
        authorization.require(Permission.TOUR_READ);
        return response(codes.findById(id).orElseThrow(TourCodeServiceImpl::notFound));
    }

    @Override
    public TourCodeResponse create(CreateTourCodeRequest request) {
        authorization.require(Permission.TOUR_MANAGE);
        // Serialize code changes per tour so "one active code" is checked under a lock.
        tours.findLockedById(request.tourId()).orElseThrow(() ->
                new TourException(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Tour was not found"));
        if (codes.findFirstByTourIdAndDeletedAtIsNull(request.tourId()).isPresent()) {
            throw conflict("TOUR_CODE_ALREADY_ASSIGNED", "This tour already has an active code; rename or delete it first");
        }
        TourCode code = insert(request.tourId(), normalize(request.code()));
        audit(code, "TOUR_CODE_CREATED", null, Map.of("tourId", code.getTourId(), "code", code.getCode()));
        return response(code);
    }

    @Override
    public TourCodeResponse rename(long id, UpdateTourCodeRequest request) {
        authorization.require(Permission.TOUR_MANAGE);
        TourCode current = requireActiveForUpdate(id);
        tours.findLockedById(current.getTourId());
        String newCode = normalize(request.code());
        if (newCode.equals(current.getCode())) {
            throw new TourException(HttpStatus.BAD_REQUEST, "TOUR_CODE_UNCHANGED", "The new code is the same as the current code");
        }
        String reason = request.reason().trim();
        retire(current, reason);
        TourCode replacement = insert(current.getTourId(), newCode);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tourId", current.getTourId());
        details.put("oldCode", current.getCode());
        details.put("newCode", newCode);
        details.put("replacedById", replacement.getId());
        audit(current, "TOUR_CODE_RENAMED", reason, details);
        audit(replacement, "TOUR_CODE_CREATED", reason,
                Map.of("tourId", replacement.getTourId(), "code", newCode, "replacesId", current.getId()));
        return response(replacement);
    }

    @Override
    public TourCodeResponse delete(long id, DeleteTourCodeRequest request) {
        authorization.require(Permission.TOUR_MANAGE);
        TourCode code = requireActiveForUpdate(id);
        String reason = request.reason().trim();
        retire(code, reason);
        audit(code, "TOUR_CODE_DELETED", reason, Map.of("tourId", code.getTourId(), "code", code.getCode()));
        return response(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TourCodeLogResponse> logs(long tourId) {
        authorization.require(Permission.TOUR_READ);
        List<Long> codeIds = codes.findAllByTourIdOrderByCreatedAtDescIdDesc(tourId).stream().map(TourCode::getId).toList();
        if (codeIds.isEmpty()) return List.of();
        return audits.findAllByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(ENTITY_TYPE, codeIds).stream()
                .map(log -> new TourCodeLogResponse(log.getId(), log.getAction(), log.getEntityId(), log.getActorId(),
                        log.getActor() == null ? null : log.getActor().getEmail(), log.getReason(), log.getDetails(),
                        log.getOccurredAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> activeCode(long tourId) {
        return codes.findFirstByTourIdAndDeletedAtIsNull(tourId).map(TourCode::getCode);
    }

    private TourCode insert(long tourId, String value) {
        if (codes.existsByCode(value)) {
            throw conflict("TOUR_CODE_EXISTS", "Tour code " + value + " is already used or was used before");
        }
        TourCode code = new TourCode();
        code.setTourId(tourId);
        code.setCode(value);
        code.setCreatedBy(authorization.currentAccountId());
        try {
            TourCode saved = codes.saveAndFlush(code);
            entities.refresh(saved); // load DB-generated timestamps and the tour association
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw conflict("TOUR_CODE_EXISTS", "Tour code " + value + " is already used or was used before");
        }
    }

    private void retire(TourCode code, String reason) {
        code.setDeletedAt(OffsetDateTime.now(clock));
        code.setDeletedBy(authorization.currentAccountId());
        code.setDeleteReason(reason);
        entities.refresh(codes.saveAndFlush(code));
    }

    private TourCode requireActiveForUpdate(long id) {
        TourCode code = codes.findForUpdateById(id).orElseThrow(TourCodeServiceImpl::notFound);
        if (code.isDeleted()) throw conflict("TOUR_CODE_DELETED", "This tour code was already deleted");
        return code;
    }

    private void audit(TourCode code, String action, String reason, Map<String, Object> details) {
        var audit = new AuditLog();
        audit.setActorId(authorization.currentAccountId());
        audit.setAction(action);
        audit.setEntityType(ENTITY_TYPE);
        audit.setEntityId(code.getId());
        audit.setReason(reason);
        audit.setDetails(json.writeValueAsString(details));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        audits.saveAndFlush(audit);
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static TourCodeResponse response(TourCode code) {
        return new TourCodeResponse(code.getId(), code.getTourId(),
                code.getTour() == null ? null : code.getTour().getTitle(), code.getCode(), !code.isDeleted(),
                code.getCreatedBy(), code.getCreatedAt(), code.getDeletedAt(), code.getDeletedBy(),
                code.getDeleteReason());
    }

    private static TourException notFound() {
        return new TourException(HttpStatus.NOT_FOUND, "TOUR_CODE_NOT_FOUND", "Tour code was not found");
    }

    private static TourException conflict(String code, String message) {
        return new TourException(HttpStatus.CONFLICT, code, message);
    }
}
