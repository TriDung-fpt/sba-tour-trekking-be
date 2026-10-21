package com.trekking.app.features.tour.repository;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourBookingRepository extends JpaRepository<Booking, Long> {
    @Query("""
            select coalesce(sum(b.participantCount), 0)
            from Booking b
            where b.scheduleId = :scheduleId
              and (b.status = :confirmed or (b.status = :pending and b.holdExpiresAt > :now))
            """)
    long countOccupiedSeats(@Param("scheduleId") Long scheduleId,
                            @Param("confirmed") BookingStatus confirmed,
                            @Param("pending") BookingStatus pending,
                            @Param("now") OffsetDateTime now);

    @Query("select coalesce(sum(b.participantCount), 0) from Booking b where b.scheduleId = :scheduleId and b.status in :statuses")
    long countOperationalParticipants(@Param("scheduleId") Long scheduleId,
                                      @Param("statuses") List<BookingStatus> statuses);

    int countByScheduleIdAndStatusAndHoldExpiresAtAfter(Long scheduleId, BookingStatus status,
                                                        OffsetDateTime now);

    @Query("""
            select b.bookingCode, p.id, p.fullName, p.phone, p.checkedInAt, p.checkInNote
            from Booking b join Participant p on p.bookingId = b.id
            where b.scheduleId = :scheduleId and b.companyId = :companyId
              and b.status in :visibleStatuses
            order by b.createdAt, p.id
            """)
    List<Object[]> findRosterRows(@Param("scheduleId") Long scheduleId, @Param("companyId") Long companyId,
                                  @Param("visibleStatuses") List<BookingStatus> visibleStatuses,
                                  Pageable pageable);

    long countByScheduleIdAndStatusIn(Long scheduleId, List<BookingStatus> statuses);
}
