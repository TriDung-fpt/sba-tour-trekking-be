package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.CreateActivityRequest;
import com.trekking.app.features.tour.dto.request.UpdateActivityRequest;
import com.trekking.app.features.tour.dto.response.ActivityResponse;
import java.util.List;

public interface TourActivityService {
    List<ActivityResponse> list(long tourId);
    List<ActivityResponse> listPublic(long tourId);
    ActivityResponse create(long tourId, CreateActivityRequest request);
    ActivityResponse update(long tourId, long activityId, UpdateActivityRequest request);
    void delete(long tourId, long activityId);
}
