package com.trekking.app.features.support.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.service.audit.AuditWriter;
import com.trekking.app.features.support.dto.response.ReviewResponse;
import com.trekking.app.features.support.entity.Review;
import com.trekking.app.features.support.enums.ReviewStatus;
import com.trekking.app.features.support.repository.ReviewRepository;
import com.trekking.app.features.support.service.ReviewModerationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewModerationServiceImpl implements ReviewModerationService {
    private final ReviewRepository reviews;
    private final AuthorizationService authorization;
    private final AuditWriter audits;

    public ReviewModerationServiceImpl(ReviewRepository reviews, AuthorizationService authorization, AuditWriter audits) {
        this.reviews = reviews;
        this.authorization = authorization;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> list(String status, Long bookingId, int page, int size) {
        authorization.require(Permission.REVIEW_MODERATE);
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        return reviews.search(parseStatus(status), bookingId, PageRequest.of(page, size)).map(ReviewModerationServiceImpl::response);
    }

    @Transactional
    public ReviewResponse hide(Long id, String inputReason) {
        authorization.require(Permission.REVIEW_MODERATE);
        String reason = reason(inputReason);
        Review review = reviews.lockById(id).orElseThrow(ReviewModerationServiceImpl::notFound);
        if (review.getStatus() == ReviewStatus.HIDDEN) throw conflict("REVIEW_ALREADY_HIDDEN");
        var before = state(review);
        review.setStatus(ReviewStatus.HIDDEN);
        review.setModeratedBy(authorization.currentAccountId());
        review.setModerationReason(reason);
        audits.record("review", id, "REVIEW_HIDDEN", authorization.currentAccountId(), reason,
                before, state(review));
        return response(review);
    }

    @Transactional
    public ReviewResponse restore(Long id, String inputReason) {
        authorization.require(Permission.REVIEW_MODERATE);
        String reason = reason(inputReason);
        Review review = reviews.lockById(id).orElseThrow(ReviewModerationServiceImpl::notFound);
        if (review.getStatus() == ReviewStatus.VISIBLE) throw conflict("REVIEW_ALREADY_VISIBLE");
        var before = state(review);
        review.setStatus(ReviewStatus.VISIBLE);
        review.setModeratedBy(null);
        review.setModerationReason(null);
        audits.record("review", id, "REVIEW_RESTORED", authorization.currentAccountId(), reason,
                before, state(review));
        return response(review);
    }

    private static ReviewResponse response(Review review) {
        return new ReviewResponse(review.getId(), review.getBookingId(), review.getAuthorId(),
                review.getTourRating(), review.getServiceRating(), review.getContent(),
                review.getStatus().getValue(), review.getModeratedBy(), review.getModerationReason(),
                review.getCreatedAt());
    }

    private record ModerationState(String status, Long moderatedBy, String moderationReason) {}

    private static ModerationState state(Review review) {
        return new ModerationState(review.getStatus().getValue(), review.getModeratedBy(),
                review.getModerationReason());
    }

    private static ReviewStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (ReviewStatus status : ReviewStatus.values()) {
            if (status.name().equalsIgnoreCase(raw) || status.getValue().equalsIgnoreCase(raw)) return status;
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid status filter");
    }

    private static String reason(String input) {
        String value = input == null ? null : input.strip();
        if (value == null || value.isBlank() || value.length() > 2000) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REASON",
                    "A reason of at most 2000 characters is required");
        }
        return value;
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review was not found");
    }

    private static AuthException conflict(String code) {
        return new AuthException(HttpStatus.CONFLICT, code, "Review status does not allow this transition");
    }
}
