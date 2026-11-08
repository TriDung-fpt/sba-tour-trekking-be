package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.ScheduleGuide;
import com.trekking.app.features.tour.entity.ScheduleGuideId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleGuideRepository extends JpaRepository<ScheduleGuide, ScheduleGuideId> {
    @org.springframework.data.jpa.repository.Query("select sg from ScheduleGuide sg join fetch sg.guide g join fetch g.account a where sg.scheduleId = :scheduleId order by sg.isLead desc, a.fullName")
    java.util.List<ScheduleGuide> findDetailsByScheduleId(long scheduleId);

    @org.springframework.data.jpa.repository.Query("select sg from ScheduleGuide sg join fetch sg.schedule s join fetch s.tour t where sg.guideId = :guideId order by s.startsAt asc, s.id asc")
    java.util.List<ScheduleGuide> findAssignmentsWithScheduleByGuideId(long guideId);

    boolean existsByScheduleIdAndGuideId(Long scheduleId, Long guideId);

    @org.springframework.data.jpa.repository.Query("select count(sg) > 0 from ScheduleGuide sg join sg.schedule s where sg.guideId = :guideId and sg.scheduleId <> :scheduleId and s.status in (com.trekking.app.features.tour.enums.ScheduleStatus.SCHEDULED, com.trekking.app.features.tour.enums.ScheduleStatus.ONGOING) and s.startsAt < :endsAt and s.endsAt > :startsAt")
    boolean hasOverlappingAssignment(long guideId, long scheduleId,
                                     java.time.OffsetDateTime startsAt, java.time.OffsetDateTime endsAt);

    java.util.List<ScheduleGuide> findAllByScheduleId(long scheduleId);
    java.util.List<ScheduleGuide> findAllByGuideId(long guideId);
    void deleteAllByScheduleId(long scheduleId);
}
