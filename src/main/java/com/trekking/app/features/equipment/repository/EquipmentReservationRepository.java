package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentReservation;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentReservationRepository extends JpaRepository<EquipmentReservation, Long> {
    @Query(value = """
            select e.id as "equipmentId", e.name as "equipmentName", sum(ri.quantity) as "quantity"
            from trekking.equipment_reservation_items ri
            join trekking.equipment_reservations r on r.id = ri.reservation_id
            join trekking.tour_schedules s on s.id = r.schedule_id
            join trekking.equipment e on e.id = ri.equipment_id
            where s.tour_id = :tourId and r.status in ('confirmed', 'issued', 'return_required', 'returned')
            group by e.id, e.name
            order by e.name, e.id
            """, nativeQuery = true)
    List<EquipmentTotal> summarizeBookedEquipment(@Param("tourId") long tourId);

    @Query(value = """
            select e.id as "equipmentId",
                   e.name as "equipmentName",
                   coalesce(sum(case
                       when b.status in ('confirmed', 'completed')
                        and r.status in ('confirmed', 'issued', 'return_required', 'returned')
                       then ri.quantity else 0 end), 0) as "orderedQuantity",
                   coalesce(sum(case
                       when b.status in ('confirmed', 'completed')
                        and r.status in ('confirmed', 'issued', 'return_required', 'returned')
                       then ri.issued_quantity else 0 end), 0) as "issuedQuantity",
                   coalesce(sum(case
                       when b.status in ('confirmed', 'completed')
                        and r.status in ('confirmed', 'issued', 'return_required', 'returned')
                       then ri.returned_quantity else 0 end), 0) as "returnedQuantity",
                   count(distinct case
                       when b.status in ('confirmed', 'completed')
                        and r.status in ('confirmed', 'issued', 'return_required', 'returned')
                       then b.id else null end) as "bookingCount",
                   coalesce(sum(case
                       when b.status = 'pending_payment'
                        and r.status = 'held'
                        and r.hold_expires_at > :now
                        and b.hold_expires_at > :now
                       then ri.quantity else 0 end), 0) as "activeHoldQuantity",
                   count(distinct case
                       when b.status = 'pending_payment'
                        and r.status = 'held'
                        and r.hold_expires_at > :now
                        and b.hold_expires_at > :now
                       then b.id else null end) as "activeHoldBookingCount"
            from trekking.equipment_reservation_items ri
            join trekking.equipment_reservations r on r.id = ri.reservation_id
            join trekking.bookings b on b.id = r.booking_id
            join trekking.equipment e on e.id = ri.equipment_id
            where r.schedule_id = :scheduleId
              and r.status <> 'released'
              and b.status not in ('cancelled', 'expired', 'no_show')
            group by e.id, e.name
            having coalesce(sum(case
                       when b.status in ('confirmed', 'completed')
                        and r.status in ('confirmed', 'issued', 'return_required', 'returned')
                       then ri.quantity else 0 end), 0) > 0
                or coalesce(sum(case
                       when b.status = 'pending_payment'
                        and r.status = 'held'
                        and r.hold_expires_at > :now
                        and b.hold_expires_at > :now
                       then ri.quantity else 0 end), 0) > 0
            order by e.name, e.id
            """, nativeQuery = true)
    List<ScheduleEquipmentAggregationRow> summarizeScheduleEquipment(
            @Param("scheduleId") long scheduleId,
            @Param("now") OffsetDateTime now);

    interface EquipmentTotal {
        Long getEquipmentId();
        String getEquipmentName();
        Long getQuantity();
    }

    interface ScheduleEquipmentAggregationRow {
        Long getEquipmentId();
        String getEquipmentName();
        Long getOrderedQuantity();
        Long getIssuedQuantity();
        Long getReturnedQuantity();
        Long getBookingCount();
        Long getActiveHoldQuantity();
        Long getActiveHoldBookingCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EquipmentReservation r where r.bookingId = :bookingId")
    Optional<EquipmentReservation> findByBookingIdForUpdate(@Param("bookingId") long bookingId);

    Optional<EquipmentReservation> findByBookingId(long bookingId);

    List<EquipmentReservation> findAllByScheduleIdOrderByIdAsc(long scheduleId);

    @Query(value = """
            select coalesce(sum(case
              when r.status in ('held','confirmed') and (r.status <> 'held' or r.hold_expires_at > :now)
                and s.starts_at < :endsAt and s.ends_at > :startsAt then ri.quantity
              when r.status in ('issued','return_required') then ri.issued_quantity - ri.returned_quantity
              else 0 end), 0)
            from trekking.equipment_reservation_items ri
            join trekking.equipment_reservations r on r.id = ri.reservation_id
            join trekking.tour_schedules s on s.id = r.schedule_id
            where ri.equipment_id = :equipmentId
            """, nativeQuery = true)
    Long committedQuantity(@Param("equipmentId") long equipmentId, @Param("startsAt") OffsetDateTime startsAt,
                           @Param("endsAt") OffsetDateTime endsAt, @Param("now") OffsetDateTime now);

    @Query(value = """
            select coalesce(sum(case
              when r.status in ('held','confirmed') and (r.status <> 'held' or r.hold_expires_at > :now) then ri.quantity
              when r.status in ('issued','return_required') then ri.issued_quantity - ri.returned_quantity
              else 0 end), 0)
            from trekking.equipment_reservation_items ri
            join trekking.equipment_reservations r on r.id = ri.reservation_id
            where ri.equipment_id = :equipmentId
            """, nativeQuery = true)
    Long committedQuantityOverall(@Param("equipmentId") long equipmentId, @Param("now") OffsetDateTime now);

    @Query("select r from EquipmentReservation r where r.status = 'held' and r.holdExpiresAt <= :now order by r.id")
    List<EquipmentReservation> findExpiredHolds(@Param("now") OffsetDateTime now, Pageable pageable);
}
