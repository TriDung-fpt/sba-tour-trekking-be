package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpiredBookingHoldProcessor {
    private final BookingRepository bookings;
    private final TourScheduleRepository schedules;
    private final AuditLogRepository auditLogs;

    public ExpiredBookingHoldProcessor(BookingRepository bookings, TourScheduleRepository schedules,
                                       AuditLogRepository auditLogs) {
        this.bookings = bookings;
        this.schedules = schedules;
        this.auditLogs = auditLogs;
    }

    @Scheduled(fixedDelayString = "${booking.expiry.poll-ms:30000}")
    @Transactional
    public void expireHolds() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long id : bookings.lockExpiredIds(now)) {
            var booking = bookings.findById(id).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT
                    || booking.getHoldExpiresAt().isAfter(now)) continue;
            var schedule = schedules.findByIdForBooking(booking.getScheduleId()).orElse(null);
            if (schedule == null) continue;
            booking.setStatus(BookingStatus.EXPIRED);
            schedule.setHeldSeats(Math.max(0, schedule.getHeldSeats() - booking.getParticipantCount()));
            bookings.save(booking);
            schedules.save(schedule);
            AuditLog audit = new AuditLog();
            audit.setAction("BOOKING_HOLD_EXPIRED"); audit.setEntityType("booking"); audit.setEntityId(id);
            audit.setReason("Payment hold expired"); audit.setDetails("{}"); audit.setOccurredAt(now);
            auditLogs.save(audit);
        }
    }
}
