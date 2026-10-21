package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.response.Guide;
import com.trekking.app.features.tour.entity.ScheduleGuide;
import com.trekking.app.features.tour.entity.ScheduleGuideId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourScheduleGuideRepository extends JpaRepository<ScheduleGuide, ScheduleGuideId> {
    boolean existsByScheduleIdAndGuideIdAndCompanyId(Long scheduleId, Long guideId, Long companyId);

    List<ScheduleGuide> findAllByScheduleIdOrderByIsLeadDescGuideIdAsc(Long scheduleId);

    @Query("select sg.guide.account.fullName from ScheduleGuide sg where sg.scheduleId = :scheduleId order by sg.isLead desc, sg.guide.account.fullName")
    List<String> findGuideNames(@Param("scheduleId") Long scheduleId);

    @Query("select new com.trekking.app.features.tour.dto.response.Guide(sg.guideId, sg.guide.account.fullName, sg.isLead) from ScheduleGuide sg where sg.scheduleId = :scheduleId order by sg.isLead desc, sg.guide.account.fullName")
    List<Guide> findGuideResponses(@Param("scheduleId") Long scheduleId);

    void deleteAllByScheduleId(Long scheduleId);
}
