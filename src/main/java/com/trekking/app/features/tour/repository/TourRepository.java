package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.enums.TourStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourRepository extends JpaRepository<Tour, Long>, JpaSpecificationExecutor<Tour> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tour t where t.id = :id")
    Optional<Tour> findLockedById(@Param("id") long id);

    @Query(value = """
            select t.id as tourId, t.title as title, t.status as status, count(s.id) as scheduleCount
            from Tour t left join TourSchedule s on s.tourId = t.id
            where t.status <> com.trekking.app.features.tour.enums.TourStatus.ARCHIVED
              and locate(:query, lower(t.title)) > 0
            group by t.id, t.title, t.status order by t.id desc
            """, countQuery = """
            select count(t) from Tour t
            where t.status <> com.trekking.app.features.tour.enums.TourStatus.ARCHIVED
              and locate(:query, lower(t.title)) > 0
            """)
    Page<ScheduleOverview> findScheduleOverview(@Param("query") String query, Pageable pageable);

    interface ScheduleOverview {
        Long getTourId();
        String getTitle();
        TourStatus getStatus();
        Long getScheduleCount();
    }
}
