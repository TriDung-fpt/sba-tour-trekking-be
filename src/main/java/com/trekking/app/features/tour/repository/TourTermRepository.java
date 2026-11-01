package com.trekking.app.features.tour.repository;
import com.trekking.app.features.tour.entity.TourTerm;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TourTermRepository extends JpaRepository<TourTerm, Long> {
    List<TourTerm> findAllByTourIdOrderByDisplayOrderAsc(long tourId);
    void deleteAllByTourId(long tourId);
}
