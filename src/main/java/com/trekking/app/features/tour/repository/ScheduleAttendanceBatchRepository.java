package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.ScheduleAttendanceBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleAttendanceBatchRepository extends JpaRepository<ScheduleAttendanceBatch, Long> {
    Optional<ScheduleAttendanceBatch> findByScheduleIdAndRoundType(Long scheduleId, String roundType);
    List<ScheduleAttendanceBatch> findAllByScheduleIdOrderByIdAsc(Long scheduleId);
    List<ScheduleAttendanceBatch> findAllByScheduleIdIn(List<Long> scheduleIds);
}
