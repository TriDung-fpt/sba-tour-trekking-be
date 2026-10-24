package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.ScheduleGuide;
import com.trekking.app.features.tour.entity.ScheduleGuideId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleGuideRepository extends JpaRepository<ScheduleGuide, ScheduleGuideId> {
    boolean existsByScheduleIdAndGuideId(Long scheduleId, Long guideId);
}
