package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.ReorderTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UploadTourMediaRequest;
import com.trekking.app.features.tour.dto.response.TourMediaResponse;
import java.util.List;

public interface TourMediaService {
    TourMediaResponse upload(long tourId, UploadTourMediaRequest request);
    TourMediaResponse updateCaption(long tourId, long mediaId, UpdateTourMediaRequest request);
    TourMediaResponse setCover(long tourId, long mediaId);
    List<TourMediaResponse> reorder(long tourId, ReorderTourMediaRequest request);
    void delete(long tourId, long mediaId);
}
