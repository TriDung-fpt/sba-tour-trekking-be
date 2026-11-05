package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentReservation;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentReservationRepository extends JpaRepository<EquipmentReservation, Long> {
    boolean existsByPackageId(long packageId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EquipmentReservation r where r.bookingId = :bookingId")
    Optional<EquipmentReservation> findByBookingIdForUpdate(@Param("bookingId") long bookingId);

    Optional<EquipmentReservation> findByBookingId(long bookingId);

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
