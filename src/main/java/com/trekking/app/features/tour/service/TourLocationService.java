package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.SaveTourLocationsRequest;
import com.trekking.app.features.tour.dto.request.CreateLocationRequest;
import com.trekking.app.features.tour.dto.response.LocationResponse;
import java.util.List;

public interface TourLocationService {
    List<LocationResponse> listAvailable();
    LocationResponse createLocation(CreateLocationRequest request);
    List<LocationResponse> listForTour(long tourId);
    List<LocationResponse> listPublicForTour(long tourId);
    List<LocationResponse> save(long tourId, SaveTourLocationsRequest request);
}
