package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.ScheduleParticipantAttendance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleParticipantAttendanceRepository extends JpaRepository<ScheduleParticipantAttendance, Long> {
    List<ScheduleParticipantAttendance> findAllByScheduleIdOrderByIdAsc(Long scheduleId);
    Optional<ScheduleParticipantAttendance> findByParticipantId(Long participantId);
}
