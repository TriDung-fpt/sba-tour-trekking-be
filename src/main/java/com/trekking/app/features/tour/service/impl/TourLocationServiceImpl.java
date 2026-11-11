package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.tour.dto.request.SaveTourLocationsRequest;
import com.trekking.app.features.tour.dto.request.CreateLocationRequest;
import com.trekking.app.features.tour.dto.response.LocationResponse;
import com.trekking.app.features.tour.entity.TourLocation;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.LocationRepository;
import com.trekking.app.features.tour.repository.TourLocationRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.service.TourLocationService;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TourLocationServiceImpl implements TourLocationService {
    private final LocationRepository locations;
    private final TourLocationRepository tourLocations;
    private final TourRepository tours;
    private final AuthorizationService authorization;

    public TourLocationServiceImpl(LocationRepository locations, TourLocationRepository tourLocations,
                                   TourRepository tours, AuthorizationService authorization) {
        this.locations = locations;
        this.tourLocations = tourLocations;
        this.tours = tours;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationResponse> listAvailable() {
        authorization.require(Permission.TOUR_COMPOSE);
        return locations.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                .map(location -> response(location, null)).toList();
    }

    @Override
    public LocationResponse createLocation(CreateLocationRequest request) {
        authorization.require(Permission.TOUR_COMPOSE);
        var location = new com.trekking.app.features.tour.entity.Location();
        location.setName(request.name().trim());
        location.setProvinceName(request.provinceName() == null || request.provinceName().isBlank()
                ? null : request.provinceName().trim());
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        return response(locations.saveAndFlush(location), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationResponse> listForTour(long tourId) {
        authorization.require(Permission.TOUR_COMPOSE);
        if (!tours.existsById(tourId)) throw notFound();
        var links = tourLocations.findAllByTourIdOrderByVisitOrderAsc(tourId);
        var byId = locations.findAllById(links.stream().map(TourLocation::getLocationId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(com.trekking.app.features.tour.entity.Location::getId,
                        location -> location));
        return links.stream().map(link -> {
            var location = byId.get(link.getLocationId());
            if (location == null) throw new TourException(HttpStatus.CONFLICT, "LOCATION_LINK_INVALID", "Linked location no longer exists");
            return response(location.getId(), location.getName(), location.getProvinceName(),
                    location.getLatitude(), location.getLongitude(), link.getVisitOrder());
        }).toList();
    }

    @Override
    public List<LocationResponse> save(long tourId, SaveTourLocationsRequest request) {
        authorization.require(Permission.TOUR_COMPOSE);
        if (!tours.existsById(tourId)) throw notFound();
        var ids = request.locationIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "DUPLICATE_LOCATION", "Locations must be unique");
        }
        var selected = locations.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_LOCATION", "Choose existing locations");
        }
        tourLocations.deleteAllByTourId(tourId);
        tourLocations.flush();
        for (int index = 0; index < ids.size(); index++) {
            var link = new TourLocation();
            link.setTourId(tourId);
            link.setLocationId(ids.get(index));
            link.setVisitOrder(index + 1);
            tourLocations.save(link);
        }
        tourLocations.flush();
        return listForTour(tourId);
    }

    private LocationResponse response(com.trekking.app.features.tour.entity.Location location, Integer order) {
        return response(location.getId(), location.getName(), location.getProvinceName(),
                location.getLatitude(), location.getLongitude(), order);
    }

    private LocationResponse response(long id, String name, String province, java.math.BigDecimal latitude,
                                      java.math.BigDecimal longitude, Integer order) {
        return new LocationResponse(id, name, province, latitude, longitude, order);
    }

    private TourException notFound() {
        return new TourException(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Tour not found");
    }
}
