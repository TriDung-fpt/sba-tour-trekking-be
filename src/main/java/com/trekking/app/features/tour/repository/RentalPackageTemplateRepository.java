package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.RentalPackageTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalPackageTemplateRepository extends JpaRepository<RentalPackageTemplate, Long> {
    List<RentalPackageTemplate> findAllByOrderByTitleAscIdAsc();
}
