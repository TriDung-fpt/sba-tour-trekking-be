package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.PackingItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingItemRepository extends JpaRepository<PackingItem, Long> {
    List<PackingItem> findAllByTourIdOrderByDisplayOrderAscIdAsc(long tourId);
}
