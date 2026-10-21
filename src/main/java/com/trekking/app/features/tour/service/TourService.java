package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;




import java.util.List;

public interface TourService {
    TourPageResponse searchPublic(TourSearchRequest request);

    TourDetailResponse getPublicDetail(long tourId);

    ReviewPage publicReviews(long tourId, int page, int size);

    List<LocationResponse> listLocations();
}
