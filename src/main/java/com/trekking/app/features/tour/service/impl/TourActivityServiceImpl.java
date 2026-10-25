package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.CreateActivityRequest;
import com.trekking.app.features.tour.dto.request.UpdateActivityRequest;
import com.trekking.app.features.tour.dto.response.ActivityResponse;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.mapper.TourActivityMapper;
import com.trekking.app.features.tour.repository.TourActivityRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.service.TourActivityService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TourActivityServiceImpl implements TourActivityService {
    private final TourRepository tours;
    private final TourActivityRepository activities;
    private final TourActivityMapper mapper;

    public TourActivityServiceImpl(TourRepository tours, TourActivityRepository activities, TourActivityMapper mapper) {
        this.tours = tours;
        this.activities = activities;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> list(long tourId) {
        requireTour(tourId);
        return activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAscIdAsc(tourId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> listPublic(long tourId) {
        requirePublishedTour(tourId);
        return activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAscIdAsc(tourId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public ActivityResponse create(long tourId, CreateActivityRequest request) {
        requireTour(tourId);
        var activity = mapper.toEntity(request, tourId);
        return mapper.toResponse(activities.saveAndFlush(activity));
    }

    @Override
    public ActivityResponse update(long tourId, long activityId, UpdateActivityRequest request) {
        var activity = requireActivity(tourId, activityId);
        mapper.update(request, activity);
        return mapper.toResponse(activities.saveAndFlush(activity));
    }

    @Override
    public void delete(long tourId, long activityId) {
        activities.delete(requireActivity(tourId, activityId));
    }

    private void requireTour(long tourId) {
        if (!tours.existsById(tourId)) {
            throw new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
        }
    }

    private Tour requirePublishedTour(long tourId) {
        var tour = tours.findById(tourId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found"));
        if (tour.getStatus() != TourStatus.PUBLISHED) {
            throw new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
        }
        return tour;
    }

    private com.trekking.app.features.tour.entity.TourActivity requireActivity(long tourId, long activityId) {
        return activities.findByIdAndTourId(activityId, tourId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Activity was not found"));
    }
}
