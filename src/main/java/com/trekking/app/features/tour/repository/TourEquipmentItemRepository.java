package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourEquipmentItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourEquipmentItemRepository extends JpaRepository<TourEquipmentItem, Long> {
    @EntityGraph(attributePaths = "equipment")
    List<TourEquipmentItem> findAllByPackageIdOrderByIdAsc(long packageId);
    void deleteAllByPackageId(long packageId);
    boolean existsByEquipmentId(long equipmentId);
}
