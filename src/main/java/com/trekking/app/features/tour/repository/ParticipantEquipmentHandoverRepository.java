package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.ParticipantEquipmentHandover;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantEquipmentHandoverRepository extends JpaRepository<ParticipantEquipmentHandover, Long> {
    List<ParticipantEquipmentHandover> findAllByScheduleIdOrderByIdAsc(Long scheduleId);
    Optional<ParticipantEquipmentHandover> findByScheduleIdAndParticipantIdAndEquipmentId(
            Long scheduleId, Long participantId, Long equipmentId);
}
