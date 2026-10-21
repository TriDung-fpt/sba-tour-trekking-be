package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;






import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.repository.LocationRepository;
import com.trekking.app.features.tour.repository.TourActivityRepository;
import com.trekking.app.features.tour.repository.TourLocationRepository;
import com.trekking.app.features.tour.repository.TourMediaRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.repository.TourEquipmentPackageRepository;
import com.trekking.app.features.tour.repository.PackingItemRepository;
import com.trekking.app.features.tour.service.TourService;
import com.trekking.app.features.tour.service.TourAccessService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TourServiceImpl implements TourService {
    private final TourRepository tours;
    private final LocationRepository locations;
    private final TourLocationRepository tourLocations;
    private final TourMediaRepository media;
    private final TourActivityRepository activities;
    private final TourScheduleRepository schedules;
    private final TourAccessService access;
    private final TourEquipmentPackageRepository equipmentPackages;
    private final PackingItemRepository packingItems;
    private final Clock clock;

    public TourServiceImpl(TourRepository tours, LocationRepository locations, TourLocationRepository tourLocations,
                           TourMediaRepository media, TourActivityRepository activities,
                           TourScheduleRepository schedules, TourAccessService access,
                           TourEquipmentPackageRepository equipmentPackages, PackingItemRepository packingItems, Clock clock) {
        this.tours = tours;
        this.locations = locations;
        this.tourLocations = tourLocations;
        this.media = media;
        this.activities = activities;
        this.schedules = schedules;
        this.access = access;
        this.equipmentPackages = equipmentPackages;
        this.packingItems = packingItems;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TourPageResponse searchPublic(TourSearchRequest request) {
        var now = OffsetDateTime.now(clock);
        var page = tours.searchPublished(TourStatus.PUBLISHED, CompanyStatus.APPROVED,
                ScheduleStatus.SCHEDULED, request.query() == null ? "" : request.query(),
                request.destinationId() == null ? 0L : request.destinationId(), request.difficultyLevel(),
                request.dateFrom() == null ? now : request.dateFrom(),
                request.dateTo() == null ? OffsetDateTime.parse("9999-12-31T23:59:59Z") : request.dateTo(),
                request.minPrice() == null ? 0L : request.minPrice(),
                request.maxPrice() == null ? Long.MAX_VALUE : request.maxPrice(),
                request.minRating() == null ? java.math.BigDecimal.ZERO : request.minRating(),
                request.equipmentPackageId() == null ? 0L : request.equipmentPackageId(),
                now, request.sort(), com.trekking.app.features.support.enums.ReviewStatus.VISIBLE,
                PageRequest.of(request.page(), request.size()));
        var items = page.getContent().stream().map(tour -> summary(tour, now)).toList();
        return new TourPageResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public TourDetailResponse getPublicDetail(long tourId) {
        Tour tour = tours.findByIdAndStatusAndCompany_Status(tourId, TourStatus.PUBLISHED, CompanyStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));
        OffsetDateTime now = OffsetDateTime.now(clock);
        var tourSummary = summary(tour, now);
        Company company = tour.getCompany();
        var gallery = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream()
                .map(item -> new TourGalleryMedia(item.getId(), item.getMediaType().getValue(), item.getUrl(), item.getCaption()))
                .toList();
        var itinerary = activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAsc(tourId).stream()
                .map(item -> new TourItineraryActivity(item.getDayNumber(), item.getTitle(), item.getDescription(),
                        item.getStartTime(), item.getLocationId()))
                .toList();
        var publicSchedules = schedules.findAllByTourIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                        tourId, ScheduleStatus.SCHEDULED, now).stream()
                .filter(item -> item.getCapacity() > item.getHeldSeats() + item.getConfirmedSeats())
                .map(item -> new TourPublicSchedule(item.getId(), item.getStartsAt(), item.getEndsAt(),
                        item.getCapacity() - item.getHeldSeats() - item.getConfirmedSeats(), item.getBasePriceVnd(),
                        item.getEquipmentFeeVnd(), item.getDepositPercent(), access.scheduleGuideNames(item.getId())))
                .toList();
        var policy = tour.getCancellationPolicyVersionId() == null ? null
                : access.publicCancellationPolicy(tour.getCancellationPolicyVersionId()).orElse(null);
        var equipment = equipmentPackages.findByTourId(tourId).map(item -> new TourEquipmentDetails(item.getId(),
                item.getTitle(), item.getIncludedItems(), item.getSharedItems(), item.getPersonalItemsToBring())).orElse(null);
        var packing = packingItems.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream()
                .map(item -> new TourPackingItem(item.getId(), item.getName(), Boolean.TRUE.equals(item.getIsRequired()), item.getNote())).toList();
        var rating = access.reviewSummary(tourId);
        return new TourDetailResponse(tourSummary, tour.getDescription(), tour.getRequiredExperience(),
                tour.getIncludedServices(), tour.getExcludedServices(),
                new TourCompanySummary(company.getId(), company.getName()), gallery, itinerary, publicSchedules,
                tour.getRouteFileUrl(), policy, packing, equipment, rating.averageTourRating(), rating.reviewCount());
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPage publicReviews(long tourId, int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        tours.findByIdAndStatusAndCompany_Status(tourId, TourStatus.PUBLISHED, CompanyStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));
        var summary = access.reviewSummary(tourId);
        long count = access.reviewCount(tourId);
        var items = access.reviews(tourId, size, page * size).stream()
                .map(r -> new Review(r.getId(), r.getTourRating(), r.getCompanyRating(), r.getContent(),
                        parseImageUrls(r.getImageUrls()), r.getCreatedAt(), r.getCompanyReply())).toList();
        return new ReviewPage(items, page, size, count, (int) Math.ceil((double) count / size),
                summary.averageTourRating(), summary.reviewCount());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationResponse> listLocations() {
        return locations.findAllByOrderByProvinceNameAscNameAsc().stream().map(this::location).toList();
    }

    private TourSummary summary(Tour tour, OffsetDateTime now) {
        var destinations = tourLocations.findAllByTourIdOrderByVisitOrderAsc(tour.getId()).stream()
                .map(link -> location(link.getLocation())).toList();
        String coverImageUrl = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tour.getId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsCover())).map(item -> item.getUrl()).findFirst().orElse(null);
        Long startingPrice = schedules.findStartingPrice(tour.getId(), ScheduleStatus.SCHEDULED, now).orElse(null);
        String difficulty = tour.getDifficulty() == null ? null : tour.getDifficulty().getValue();
        var rating = access.reviewSummary(tour.getId());
        return new TourSummary(tour.getId(), tour.getTitle(), destinations, coverImageUrl,
                tour.getDurationDays(), difficulty, startingPrice, rating.averageTourRating(), rating.reviewCount());
    }

    private LocationResponse location(com.trekking.app.features.tour.entity.Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getProvinceName(),
                location.getLatitude(), location.getLongitude());
    }

    private List<String> parseImageUrls(String json) {
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(json, List.class).stream()
                    .filter(String.class::isInstance).map(String.class::cast).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
