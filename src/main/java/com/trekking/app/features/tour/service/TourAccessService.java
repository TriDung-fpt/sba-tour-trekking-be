package com.trekking.app.features.tour.service;

import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.enums.PolicyKind;
import com.trekking.app.features.support.entity.Review;
import com.trekking.app.features.support.enums.ReviewStatus;
import com.trekking.app.features.tour.dto.response.CancellationPolicyVersion;
import com.trekking.app.features.tour.dto.response.Guide;
import com.trekking.app.features.tour.dto.response.Participant;
import com.trekking.app.features.tour.dto.response.ReviewSummary;
import com.trekking.app.features.tour.dto.response.TourPolicySummary;
import com.trekking.app.features.tour.entity.ScheduleGuide;
import com.trekking.app.features.tour.entity.ScheduleGuideId;
import com.trekking.app.features.tour.repository.TourAuditLogRepository;
import com.trekking.app.features.tour.repository.TourBookingRepository;
import com.trekking.app.features.tour.repository.TourCompanyMemberRepository;
import com.trekking.app.features.tour.repository.TourGuideRepository;
import com.trekking.app.features.tour.repository.TourPolicyVersionRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourReviewRepository;
import com.trekking.app.features.tour.repository.TourScheduleGuideRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TourAccessService {
    private static final List<CompanyMemberRole> MANAGER_ROLES = List.of(
            CompanyMemberRole.OWNER, CompanyMemberRole.MANAGER);
    private static final List<BookingStatus> ROSTER_STATUSES = List.of(
            BookingStatus.CONFIRMED, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);

    private final TourRepository tours;
    private final TourCompanyMemberRepository members;
    private final TourPolicyVersionRepository policies;
    private final TourGuideRepository guides;
    private final TourScheduleGuideRepository scheduleGuides;
    private final TourBookingRepository bookings;
    private final TourAuditLogRepository auditLogs;
    private final TourReviewRepository reviews;

    public TourAccessService(TourRepository tours, TourCompanyMemberRepository members,
                             TourPolicyVersionRepository policies, TourGuideRepository guides,
                             TourScheduleGuideRepository scheduleGuides, TourBookingRepository bookings,
                             TourAuditLogRepository auditLogs, TourReviewRepository reviews) {
        this.tours = tours;
        this.members = members;
        this.policies = policies;
        this.guides = guides;
        this.scheduleGuides = scheduleGuides;
        this.bookings = bookings;
        this.auditLogs = auditLogs;
        this.reviews = reviews;
    }

    public List<Long> activeManagerCompanies(long accountId) {
        return members.findAllByAccountIdAndActiveTrueAndMemberRoleInAndCompany_StatusOrderByCompanyIdAsc(accountId, MANAGER_ROLES, CompanyStatus.APPROVED)
                .stream().map(CompanyMember::getCompanyId).toList();
    }

    public boolean isActiveMember(long companyId, long accountId) {
        return members.existsByCompanyIdAndAccountIdAndActiveTrueAndCompany_Status(companyId, accountId, CompanyStatus.APPROVED);
    }

    public boolean isActiveManager(long companyId, long accountId) {
        return members.existsByCompanyIdAndAccountIdAndActiveTrueAndMemberRoleInAndCompany_Status(
                companyId, accountId, MANAGER_ROLES, CompanyStatus.APPROVED);
    }

    public Optional<Long> activeMemberCompanyForTour(long tourId, long accountId, boolean managerOnly) {
        return tours.findActiveMemberCompanyForTour(tourId, accountId, managerOnly, MANAGER_ROLES, CompanyStatus.APPROVED);
    }

    public boolean validCancellationPolicy(long policyVersionId, long companyId, OffsetDateTime now) {
        return policies.isValidCancellationPolicy(policyVersionId, companyId, PolicyKind.CANCELLATION, now);
    }

    public List<CancellationPolicyVersion> cancellationPolicyVersions(long companyId, OffsetDateTime now) {
        return policies.findAvailableCancellationPolicies(companyId, PolicyKind.CANCELLATION, now);
    }

    public boolean validActiveGuide(long guideId, long companyId) {
        return guides.existsByIdAndCompanyIdAndActiveTrueAndAccount_Status(guideId, companyId, AccountStatus.ACTIVE);
    }

    public Optional<Long> guideIdForAccount(long accountId, long companyId) {
        return guides.findActiveGuideId(accountId, companyId, AccountStatus.ACTIVE);
    }

    public boolean assignedGuide(long scheduleId, long guideId, long companyId) {
        return scheduleGuides.existsByScheduleIdAndGuideIdAndCompanyId(scheduleId, guideId, companyId);
    }

    public List<Long> scheduleGuideIds(long scheduleId) {
        return scheduleGuides.findAllByScheduleIdOrderByIsLeadDescGuideIdAsc(scheduleId).stream()
                .map(ScheduleGuide::getGuideId).toList();
    }

    public List<String> scheduleGuideNames(long scheduleId) {
        return scheduleGuides.findGuideNames(scheduleId);
    }

    public List<Guide> scheduleGuides(long scheduleId) {
        return scheduleGuides.findGuideResponses(scheduleId);
    }

    public void replaceScheduleGuides(long scheduleId, long companyId, List<Long> guideIds) {
        scheduleGuides.deleteAllByScheduleId(scheduleId);
        if (guideIds == null || guideIds.isEmpty()) return;

        List<ScheduleGuide> assignments = new ArrayList<>(guideIds.size());
        for (int i = 0; i < guideIds.size(); i++) {
            ScheduleGuide assignment = new ScheduleGuide();
            assignment.setScheduleId(scheduleId);
            assignment.setGuideId(guideIds.get(i));
            assignment.setCompanyId(companyId);
            assignment.setIsLead(i == 0);
            assignments.add(assignment);
        }
        scheduleGuides.saveAll(assignments);
    }

    public List<Participant> roster(long scheduleId, long companyId, int limit, int offset) {
        List<Object[]> rows = bookings.findRosterRows(scheduleId, companyId, ROSTER_STATUSES,
                BookingStatus.PENDING_PAYMENT, OffsetDateTime.now(), PageRequest.of(offset / limit, limit));
        return rows.stream().map(row -> new Participant((String) row[0], (String) row[1], (String) row[2], null))
                .toList();
    }

    public long countBookings(long scheduleId) {
        return bookings.countOccupiedSeats(scheduleId, BookingStatus.CONFIRMED,
                BookingStatus.PENDING_PAYMENT, OffsetDateTime.now());
    }

    public int countUnresolvedHolds(long scheduleId) {
        return bookings.countByScheduleIdAndStatusAndHoldExpiresAtAfter(scheduleId,
                BookingStatus.PENDING_PAYMENT, OffsetDateTime.now());
    }

    public Optional<Long> lastSubmitter(long tourId) {
        return auditLogs.findFirstByEntityTypeAndEntityIdAndActionOrderByOccurredAtDescIdDesc(
                "tour", tourId, "TOUR_SUBMITTED").map(AuditLog::getActorId);
    }

    public void audit(long actorId, String action, long tourId, String reason) {
        saveAudit(actorId, action, "tour", tourId, reason);
    }

    public void auditSchedule(long actorId, String action, long scheduleId, String reason) {
        saveAudit(actorId, action, "tour_schedule", scheduleId, reason);
    }

    public ReviewSummary reviewSummary(long tourId) {
        Object[] row = reviews.summarizePublicReviews(tourId, ReviewStatus.VISIBLE)
                .stream().findFirst().orElse(new Object[] { null, 0L });
        Object average = row[0];
        BigDecimal averageRating = average == null ? null
                : average instanceof BigDecimal value ? value
                : BigDecimal.valueOf(((Number) average).doubleValue());
        return new ReviewSummary(averageRating, ((Number) row[1]).longValue());
    }

    public List<Review> reviews(long tourId, int limit, int offset) {
        return reviews.findAllByBooking_Schedule_TourIdAndStatusOrderByCreatedAtDescIdDesc(
                tourId, ReviewStatus.VISIBLE, PageRequest.of(offset / limit, limit));
    }

    public long reviewCount(long tourId) {
        return reviews.countByBooking_Schedule_TourIdAndStatus(tourId, ReviewStatus.VISIBLE);
    }

    public Optional<TourPolicySummary> publicCancellationPolicy(long versionId) {
        return policies.findPublicCancellationPolicy(versionId, PolicyKind.CANCELLATION);
    }

    private void saveAudit(long actorId, String action, String entityType, long entityId, String reason) {
        AuditLog event = new AuditLog();
        event.setActorId(actorId);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setReason(reason);
        auditLogs.save(event);
    }
}
