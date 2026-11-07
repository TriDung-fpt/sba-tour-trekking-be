package com.trekking.app.features.review.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.service.BusinessAuthorizationService;
import com.trekking.app.features.account.service.audit.AuditWriter;
import com.trekking.app.features.account.service.audit.ReviewAuditState;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.review.dto.request.CreateReviewRequest;
import com.trekking.app.features.review.dto.response.AdminReviewDetailResponse;
import com.trekking.app.features.review.dto.response.AdminReviewListItemResponse;
import com.trekking.app.features.review.dto.response.AdminReviewPageResponse;
import com.trekking.app.features.review.dto.response.CustomerReviewResponse;
import com.trekking.app.features.review.dto.response.TourReviewItemResponse;
import com.trekking.app.features.review.dto.response.TourReviewPageResponse;
import com.trekking.app.features.review.repository.ReviewRepository;
import com.trekking.app.features.review.service.ReviewService;
import com.trekking.app.features.support.entity.Review;
import com.trekking.app.features.support.enums.ReviewStatus;
import com.trekking.app.features.tour.repository.TourRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdat", "createdAt", "rating", "tourRating");

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final AppAccountRepository accounts;
    private final TourRepository tours;
    private final AuthorizationService authorization;
    private final BusinessAuthorizationService businessAuthorization;
    private final AuditWriter audits;

    public ReviewServiceImpl(ReviewRepository reviews, BookingRepository bookings, ParticipantRepository participants,
                             AppAccountRepository accounts, TourRepository tours, AuthorizationService authorization,
                             BusinessAuthorizationService businessAuthorization, AuditWriter audits) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.participants = participants;
        this.accounts = accounts;
        this.tours = tours;
        this.authorization = authorization;
        this.businessAuthorization = businessAuthorization;
        this.audits = audits;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewPageResponse listAdmin(int page, int size, String query, String status, Integer rating,
                                             Long tourId, String createdFrom, String createdTo, String sort) {
        authorization.require(Permission.REVIEW_READ);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidRequest("page must be >= 0 and size must be between 1 and 100");
        }
        ReviewStatus statusFilter = parseStatus(status);
        Short ratingFilter = parseRating(rating);
        OffsetDateTime from = parseDayStart(createdFrom, "createdFrom");
        OffsetDateTime toExclusive = parseDayStart(createdTo, "createdTo");
        String pattern = likePattern(query);
        var result = reviews.findAll(adminFilter(statusFilter, ratingFilter, tourId, from, toExclusive, pattern),
                PageRequest.of(page, size, parseSort(sort)));
        var details = detailsById(result.getContent().stream().map(Review::getId).toList());
        var items = result.getContent().stream()
                .map(review -> toAdminListItem(details.getOrDefault(review.getId(), review)))
                .toList();
        return new AdminReviewPageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewDetailResponse getAdmin(long id) {
        authorization.require(Permission.REVIEW_READ);
        return toAdminDetail(requireReviewDetail(id));
    }

    @Override
    @Transactional
    public AdminReviewDetailResponse hide(long id, String inputReason) {
        authorization.require(Permission.REVIEW_MODERATE);
        String reason = requireReason(inputReason);
        Review review = requireReview(id);
        if (review.getStatus() != ReviewStatus.VISIBLE) {
            throw new AuthException(HttpStatus.CONFLICT, "INVALID_REVIEW_TRANSITION",
                    "Review status does not allow this transition");
        }
        var before = auditState(review);
        review.setStatus(ReviewStatus.HIDDEN);
        review.setModeratedBy(authorization.currentAccountId());
        review.setModerationReason(reason);
        audits.reviewChanged(authorization.currentAccountId(), review.getId(), AuditWriter.ReviewAction.REVIEW_HIDDEN,
                reason, before, auditState(review));
        return toAdminDetail(requireReviewDetail(id));
    }

    @Override
    @Transactional
    public AdminReviewDetailResponse restore(long id, String inputReason) {
        authorization.require(Permission.REVIEW_MODERATE);
        String reason = requireReason(inputReason);
        Review review = requireReview(id);
        if (review.getStatus() != ReviewStatus.HIDDEN) {
            throw new AuthException(HttpStatus.CONFLICT, "INVALID_REVIEW_TRANSITION",
                    "Review status does not allow this transition");
        }
        var before = auditState(review);
        review.setStatus(ReviewStatus.VISIBLE);
        review.setModeratedBy(null);
        review.setModerationReason(null);
        audits.reviewChanged(authorization.currentAccountId(), review.getId(), AuditWriter.ReviewAction.REVIEW_RESTORED,
                reason, before, auditState(review));
        return toAdminDetail(requireReviewDetail(id));
    }

    @Override
    @Transactional
    public CustomerReviewResponse create(long bookingId, CreateReviewRequest request) {
        businessAuthorization.requireReview(bookingId);
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found"));
        var participant = participants.findByBookingIdAndAccountId(bookingId, booking.getCustomerId())
                .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED",
                        "Booking is not eligible for review"));
        short rating = request.rating().shortValue();
        Review review = new Review();
        review.setBookingId(bookingId);
        review.setAuthorId(booking.getCustomerId());
        review.setParticipantId(participant.getId());
        // Schema requires both ratings; API exposes a single rating mapped to both columns.
        review.setTourRating(rating);
        review.setServiceRating(rating);
        review.setContent(blankToNull(request.comment()));
        review.setEquipmentFeedback(null);
        review.setImageUrls("[]");
        review.setStatus(ReviewStatus.VISIBLE);
        reviews.saveAndFlush(review);
        return new CustomerReviewResponse(review.getId(), review.getBookingId(), review.getTourRating(),
                review.getContent(), review.getStatus().getValue(), review.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public TourReviewPageResponse listByTour(long tourId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidRequest("page must be >= 0 and size must be between 1 and 100");
        }
        if (!tours.existsById(tourId)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
        }
        var result = reviews.findVisibleByTourId(tourId, ReviewStatus.VISIBLE,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = customerNames(result.getContent().stream().map(Review::getAuthorId).toList());
        var items = result.getContent().stream()
                .map(r -> new TourReviewItemResponse(r.getId(), r.getAuthorId(),
                        names.getOrDefault(r.getAuthorId(), ""), r.getTourRating(),
                        r.getContent(), r.getCreatedAt()))
                .toList();
        Double averageRaw = reviews.averageTourRating(tourId, ReviewStatus.VISIBLE);
        BigDecimal average = BigDecimal.valueOf(averageRaw == null ? 0d : averageRaw)
                .setScale(2, RoundingMode.HALF_UP);
        long reviewCount = reviews.countByTourIdAndStatus(tourId, ReviewStatus.VISIBLE);
        return new TourReviewPageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), average, reviewCount);
    }

    private AdminReviewListItemResponse toAdminListItem(Review review) {
        Booking booking = review.getBooking();
        var schedule = booking.getSchedule();
        var tour = schedule.getTour();
        return new AdminReviewListItemResponse(
                review.getId(), review.getBookingId(), tour.getId(), tour.getTitle(), schedule.getId(),
                review.getAuthorId(), booking.getCustomer().getFullName(), review.getTourRating(),
                review.getContent(), review.getStatus().getValue(), review.getModerationReason(),
                review.getModeratedBy(), null, review.getCreatedAt());
    }

    private AdminReviewDetailResponse toAdminDetail(Review review) {
        Booking booking = review.getBooking();
        var schedule = booking.getSchedule();
        var tour = schedule.getTour();
        return new AdminReviewDetailResponse(
                review.getId(), review.getBookingId(), booking.getBookingCode(), booking.getStatus().getValue(),
                tour.getId(), tour.getTitle(), schedule.getId(), review.getAuthorId(),
                booking.getCustomer().getFullName(), review.getTourRating(), review.getContent(),
                review.getStatus().getValue(), review.getModerationReason(), review.getModeratedBy(), null,
                review.getCreatedAt());
    }

    private Review requireReview(long id) {
        return reviews.findById(id).orElseThrow(() ->
                new AuthException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review was not found"));
    }

    private Review requireReviewDetail(long id) {
        return reviews.findDetailById(id).orElseThrow(() ->
                new AuthException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review was not found"));
    }

    private Map<Long, Review> detailsById(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var map = new LinkedHashMap<Long, Review>();
        reviews.findDetailsByIds(ids).forEach(review -> map.put(review.getId(), review));
        return map;
    }

    private Map<Long, String> customerNames(List<Long> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        var names = new LinkedHashMap<Long, String>();
        accounts.findAllById(accountIds).forEach(account -> names.put(account.getId(), account.getFullName()));
        return names;
    }

    private static Specification<Review> adminFilter(ReviewStatus status, Short rating, Long tourId,
                                                     OffsetDateTime from, OffsetDateTime toExclusive, String pattern) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var booking = root.join("booking", JoinType.INNER);
            var schedule = booking.join("schedule", JoinType.INNER);
            var tour = schedule.join("tour", JoinType.INNER);
            var customer = booking.join("customer", JoinType.INNER);
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (rating != null) predicates.add(cb.equal(root.get("tourRating"), rating));
            if (tourId != null) predicates.add(cb.equal(tour.get("id"), tourId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (toExclusive != null) predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("content")), cb.literal(pattern), '\\'),
                        cb.like(cb.lower(customer.get("fullName")), cb.literal(pattern), '\\'),
                        cb.like(cb.lower(tour.get("title")), cb.literal(pattern), '\\')));
            }
            query.distinct(true);
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort parseSort(String raw) {
        Sort.Direction direction = Sort.Direction.DESC;
        String field = "createdAt";
        if (raw != null && !raw.isBlank()) {
            String[] parts = raw.split(",", -1);
            if (parts.length > 2) throw invalidRequest("sort must be createdAt or rating");
            field = SORT_FIELDS.get(parts[0].strip().toLowerCase(Locale.ROOT));
            if (field == null) throw invalidRequest("sort must be createdAt or rating");
            if (parts.length == 2) {
                String requested = parts[1].strip().toLowerCase(Locale.ROOT);
                if ("asc".equals(requested)) direction = Sort.Direction.ASC;
                else if ("desc".equals(requested)) direction = Sort.Direction.DESC;
                else throw invalidRequest("sort direction must be asc or desc");
            }
        }
        return Sort.by(new Sort.Order(direction, field), Sort.Order.desc("id"));
    }

    private static ReviewStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        for (ReviewStatus type : ReviewStatus.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) return type;
        }
        throw invalidRequest("status must be VISIBLE or HIDDEN");
    }

    private static Short parseRating(Integer rating) {
        if (rating == null) return null;
        if (rating < 1 || rating > 5) throw invalidRequest("rating must be between 1 and 5");
        return rating.shortValue();
    }

    private static OffsetDateTime parseDayStart(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.strip()).atStartOfDay(VIETNAM).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw invalidRequest(field + " must be yyyy-MM-dd");
        }
    }

    private static String likePattern(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        if (value.length() > MAX_QUERY_LENGTH) throw invalidRequest("q must be at most 200 characters");
        return "%" + value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String requireReason(String input) {
        String value = input == null ? null : input.strip();
        if (value == null || value.isBlank() || value.length() > 2000) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REASON",
                    "A reason of at most 2000 characters is required");
        }
        return value;
    }

    private static ReviewAuditState auditState(Review review) {
        return new ReviewAuditState(review.getStatus().getValue(), review.getModerationReason(), review.getModeratedBy());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static AuthException invalidRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
