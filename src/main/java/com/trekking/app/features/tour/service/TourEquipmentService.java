package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import java.util.List;

public interface TourEquipmentService {
    List<RequiredItemResponse> requiredItems(long tourId);
    TourEquipmentResponse equipment(long tourId);
}
