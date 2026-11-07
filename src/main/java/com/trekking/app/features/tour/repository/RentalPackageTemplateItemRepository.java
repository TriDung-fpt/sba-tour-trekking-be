package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.RentalPackageTemplateItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalPackageTemplateItemRepository extends JpaRepository<RentalPackageTemplateItem, Long> {
    @EntityGraph(attributePaths = "equipment")
    List<RentalPackageTemplateItem> findAllByPackageTemplateIdOrderByIdAsc(long packageTemplateId);
    void deleteAllByPackageTemplateId(long packageTemplateId);
}
