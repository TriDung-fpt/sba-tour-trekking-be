package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {}
