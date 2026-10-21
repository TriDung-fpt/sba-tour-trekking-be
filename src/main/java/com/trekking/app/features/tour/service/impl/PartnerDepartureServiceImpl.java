package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.tour.dto.response.PartnerDeparturePage;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.repository.TourBookingRepository;
import com.trekking.app.features.tour.repository.TourCompanyMemberRepository;
import com.trekking.app.features.tour.repository.TourScheduleGuideRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.PartnerDepartureService;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PartnerDepartureServiceImpl implements PartnerDepartureService {
    private static final List<BookingStatus> ROSTER_BOOKINGS = List.of(
            BookingStatus.CONFIRMED, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);
    private final TourCompanyMemberRepository members;
    private final TourScheduleRepository schedules;
    private final TourBookingRepository bookings;
    private final TourScheduleGuideRepository scheduleGuides;

    public PartnerDepartureServiceImpl(TourCompanyMemberRepository members, TourScheduleRepository schedules,
            TourBookingRepository bookings, TourScheduleGuideRepository scheduleGuides) {
        this.members = members; this.schedules = schedules; this.bookings = bookings; this.scheduleGuides = scheduleGuides;
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerDeparturePage list(long accountId, String rawStatus, OffsetDateTime from,
                                     OffsetDateTime to, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "PAGINATION", "Invalid page or size");
        if (from != null && to != null && !from.isBefore(to))
            throw error(HttpStatus.BAD_REQUEST, "DATE_RANGE", "from must be earlier than to");
        ScheduleStatus status = parseStatus(rawStatus);
        OffsetDateTime start = from == null ? OffsetDateTime.now().minusDays(1) : from;
        OffsetDateTime end = to == null ? start.plusDays(90) : to;
        var companyIds = members.findAllByAccountIdAndActiveTrueAndMemberRoleInAndCompany_StatusOrderByCompanyIdAsc(
                accountId, List.of(CompanyMemberRole.OWNER, CompanyMemberRole.MANAGER), CompanyStatus.APPROVED)
                .stream().map(m -> m.getCompanyId()).toList();
        if (companyIds.isEmpty()) return new PartnerDeparturePage(List.of(), page, size, 0, 0);
        var result = schedules.findPartnerDepartures(companyIds, status, start, end,
                status == ScheduleStatus.CANCELLED, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startsAt")));
        var items = result.getContent().stream().map(s -> new PartnerDeparturePage.Item(s.getId(), s.getTourId(),
                s.getTour().getTitle(), s.getStartsAt(), s.getEndsAt(), s.getStatus().getValue(), s.getMeetingPoint(),
                s.getContactPhone(), s.getCapacity(), s.getHeldSeats(), s.getConfirmedSeats(),
                Math.max(0, s.getCapacity() - s.getHeldSeats() - s.getConfirmedSeats()),
                bookings.countOperationalParticipants(s.getId(), ROSTER_BOOKINGS),
                scheduleGuides.findGuideNames(s.getId()))).toList();
        return new PartnerDeparturePage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private ScheduleStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return ScheduleStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST, "DEPARTURE_STATUS", "Invalid departure status"); }
    }
    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + ": " + message);
    }
}
