package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import com.trekking.app.features.tour.dto.request.AssignTourEquipmentRequest;
import java.util.List;

public interface TourEquipmentService {
    List<RequiredItemResponse> requiredItems(long tourId);
    List<RequiredItemResponse> publicRequiredItems(long tourId);
    TourEquipmentResponse equipment(long tourId);
    TourEquipmentResponse publicEquipment(long tourId);
    TourEquipmentResponse assignEquipment(long tourId, AssignTourEquipmentRequest request);
}
