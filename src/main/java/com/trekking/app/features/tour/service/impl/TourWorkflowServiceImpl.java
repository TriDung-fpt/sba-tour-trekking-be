package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;






import com.trekking.app.features.tour.entity.*;
import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.MediaType;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.repository.*;
import com.trekking.app.features.tour.service.TourAccessService;
import com.trekking.app.features.tour.service.TourApiException;
import com.trekking.app.features.tour.service.TourWorkflowService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourWorkflowServiceImpl implements TourWorkflowService {
    private final TourRepository tours;
    private final TourAccessService access;
    private final TourLocationRepository tourLocations;
    private final LocationRepository locations;
    private final TourMediaRepository media;
    private final TourActivityRepository activities;
    private final PackingItemRepository packing;
    private final TourEquipmentPackageRepository equipmentPackages;
    private final TourScheduleRepository schedules;
    private final Clock clock;

    public TourWorkflowServiceImpl(TourRepository tours, TourAccessService access, TourLocationRepository tourLocations,
                                   LocationRepository locations, TourMediaRepository media,
                                   TourActivityRepository activities, PackingItemRepository packing,
                                   TourEquipmentPackageRepository equipmentPackages, TourScheduleRepository schedules, Clock clock) {
        this.tours = tours; this.access = access; this.tourLocations = tourLocations; this.locations = locations;
        this.media = media; this.activities = activities; this.packing = packing; this.equipmentPackages = equipmentPackages;
        this.schedules = schedules; this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public Page<TourItem> partnerTours(long accountId, int page, int size, String status) {
        var companyId = singleCompany(accountId);
        validatePage(page, size);
        Page<Tour> result;
        if (status == null || status.isBlank()) result = tours.findAllByCompanyId(companyId, PageRequest.of(page, size));
        else {
            TourStatus parsed = parseStatus(status);
            result = tours.findAllByCompanyIdAndStatus(companyId, parsed, PageRequest.of(page, size));
        }
        return result.map(this::item);
    }

    @Override @Transactional(readOnly = true)
    public List<CancellationPolicyVersion> cancellationPolicyVersions(long accountId) {
        long companyId = singleCompany(accountId);
        return access.cancellationPolicyVersions(companyId, OffsetDateTime.now(clock));
    }

    @Override @Transactional
    public TourDetail createTour(long accountId, TourWriteRequest request) {
        long companyId = singleCompany(accountId);
        Tour tour = new Tour();
        tour.setCompanyId(companyId);
        apply(tour, request);
        tour.setStatus(TourStatus.DRAFT);
        tour.setReviewedBy(null); tour.setReviewedAt(null); tour.setReviewNote(null);
        Tour saved = tours.save(tour);
        replaceChildren(saved.getId(), request.locationIds(), request.media(), request.itinerary(), request.packingList());
        access.audit(accountId, "TOUR_CREATED", saved.getId(), null);
        return detail(saved);
    }

    @Override @Transactional(readOnly = true)
    public TourDetail getPartnerTour(long accountId, long tourId) {
        Tour tour = ownedTour(tourId, accountId, false);
        return detail(tour);
    }

    @Override @Transactional
    public TourDetail patchTour(long accountId, long tourId, TourPatchRequest request) {
        Tour tour = ownedTour(tourId, accountId, true);
        if (tour.getStatus() != TourStatus.DRAFT && tour.getStatus() != TourStatus.REJECTED && tour.getStatus() != TourStatus.PUBLISHED)
            throw error(HttpStatus.CONFLICT, "TOUR_STATE", "Tour cannot be edited in its current state");
        if (request.title() != null) tour.setTitle(request.title());
        if (request.description() != null) tour.setDescription(request.description());
        if (request.difficulty() != null) tour.setDifficulty(DifficultyLevel.valueOf(request.difficulty().toUpperCase(Locale.ROOT)));
        if (request.durationDays() != null) tour.setDurationDays(request.durationDays());
        if (request.requiredExperience() != null) tour.setRequiredExperience(request.requiredExperience());
        if (request.includedServices() != null) tour.setIncludedServices(request.includedServices());
        if (request.excludedServices() != null) tour.setExcludedServices(request.excludedServices());
        if (request.routeFileUrl() != null) tour.setRouteFileUrl(request.routeFileUrl());
        if (request.cancellationPolicyVersionId() != null) {
            if (!access.validCancellationPolicy(request.cancellationPolicyVersionId(), tour.getCompanyId(), OffsetDateTime.now(clock)))
                throw error(HttpStatus.BAD_REQUEST, "INVALID_POLICY", "Cancellation policy is not valid for this company");
            tour.setCancellationPolicyVersionId(request.cancellationPolicyVersionId());
        }
        boolean material = request.title() != null || request.description() != null || request.difficulty() != null
                || request.durationDays() != null || request.requiredExperience() != null || request.includedServices() != null
                || request.excludedServices() != null || request.routeFileUrl() != null || request.cancellationPolicyVersionId() != null
                || request.locationIds() != null || request.itinerary() != null || request.media() != null || request.packingList() != null;
        tours.save(tour);
        replaceOptionalChildren(tourId, request.locationIds(), request.media(), request.itinerary(), request.packingList());
        if (tour.getStatus() == TourStatus.PUBLISHED && material) {
            tour.setStatus(TourStatus.PENDING); tour.setReviewedAt(null); tour.setReviewedBy(null); tour.setReviewNote(null);
        }
        access.audit(accountId, "TOUR_UPDATED", tourId, null);
        return detail(tour);
    }

    @Override @Transactional
    public ActionMessage archiveTour(long accountId, long tourId) {
        Tour tour = ownedTour(tourId, accountId, true);
        if (tour.getStatus() == TourStatus.ARCHIVED)
            return new ActionMessage("Tour is already archived");
        var upcomingSchedules = schedules.findAllByTourIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                tourId, ScheduleStatus.SCHEDULED, OffsetDateTime.now(clock));
        if (upcomingSchedules.stream().anyMatch(s -> s.getHeldSeats() > 0 || s.getConfirmedSeats() > 0))
            throw error(HttpStatus.CONFLICT, "TOUR_HAS_BOOKINGS",
                    "Tour cannot be archived while an upcoming departure has held or confirmed seats");
        for (TourSchedule schedule : upcomingSchedules) {
            schedule.setStatus(ScheduleStatus.CANCELLED);
            schedule.setCancellationReason("Tour archived by company manager");
            schedules.save(schedule);
            access.auditSchedule(accountId, "SCHEDULE_CANCELLED", schedule.getId(), schedule.getCancellationReason());
        }
        tour.setStatus(TourStatus.ARCHIVED);
        tour.setReviewedBy(null);
        tour.setReviewedAt(null);
        tour.setReviewNote(null);
        tours.save(tour);
        access.audit(accountId, "TOUR_ARCHIVED", tourId, null);
        return new ActionMessage("Tour archived");
    }

    @Override @Transactional
    public ActionMessage restoreTour(long accountId, long tourId) {
        Tour tour = ownedTour(tourId, accountId, true);
        if (tour.getStatus() != TourStatus.ARCHIVED)
            throw error(HttpStatus.CONFLICT, "TOUR_STATE", "Only archived tours can be restored");
        tour.setStatus(TourStatus.DRAFT);
        tour.setReviewedBy(null);
        tour.setReviewedAt(null);
        tour.setReviewNote(null);
        tours.save(tour);
        access.audit(accountId, "TOUR_RESTORED", tourId, "Restored as draft; review required before publication");
        return new ActionMessage("Tour restored as draft. Submit it for review to publish it again.");
    }

    @Override @Transactional
    public ActionMessage submitTour(long accountId, long tourId) {
        Tour tour = ownedTour(tourId, accountId, true);
        if (tour.getStatus() != TourStatus.DRAFT && tour.getStatus() != TourStatus.REJECTED)
            throw error(HttpStatus.CONFLICT, "TOUR_STATE", "Only draft or rejected tours can be submitted");
        if (tour.getCancellationPolicyVersionId() == null || !access.validCancellationPolicy(tour.getCancellationPolicyVersionId(), tour.getCompanyId(), OffsetDateTime.now(clock)))
            throw error(HttpStatus.CONFLICT, "POLICY_REQUIRED", "A valid cancellation policy is required");
        if (media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).isEmpty() || tourLocations.findAllByTourIdOrderByVisitOrderAsc(tourId).isEmpty())
            throw error(HttpStatus.CONFLICT, "INCOMPLETE_TOUR", "Add at least one image and destination before submitting");
        tour.setStatus(TourStatus.PENDING); tour.setReviewedBy(null); tour.setReviewedAt(null); tour.setReviewNote(null);
        tours.save(tour); access.audit(accountId, "TOUR_SUBMITTED", tourId, null);
        return new ActionMessage("Tour submitted for review");
    }

    @Override @Transactional(readOnly = true)
    public Page<ReviewQueueItem> reviewQueue(int page, int size) {
        validatePage(page, size);
        return tours.findAllByStatus(TourStatus.PENDING, PageRequest.of(page, size)).map(t -> {
            long submitter = access.lastSubmitter(t.getId()).orElse(0L);
            return new ReviewQueueItem(t.getId(), t.getCompanyId(), t.getTitle(), t.getUpdatedAt(), submitter,
                    media.findAllByTourIdOrderByDisplayOrderAscIdAsc(t.getId()).size(),
                    (int) activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAsc(t.getId()).stream().map(TourActivity::getDayNumber).distinct().count());
        });
    }

    @Override @Transactional
    public ActionMessage approve(long adminId, long tourId) {
        Tour tour = pending(tourId); preventSelfReview(adminId, tourId);
        tour.setStatus(TourStatus.PUBLISHED); tour.setReviewedBy(adminId); tour.setReviewedAt(OffsetDateTime.now(clock)); tour.setReviewNote(null);
        tours.save(tour); access.audit(adminId, "TOUR_APPROVED", tourId, null);
        return new ActionMessage("Tour approved");
    }

    @Override @Transactional
    public ActionMessage reject(long adminId, long tourId, String reason) {
        Tour tour = pending(tourId); preventSelfReview(adminId, tourId);
        tour.setStatus(TourStatus.REJECTED); tour.setReviewedBy(adminId); tour.setReviewedAt(OffsetDateTime.now(clock)); tour.setReviewNote(reason.trim());
        tours.save(tour); access.audit(adminId, "TOUR_REJECTED", tourId, reason.trim());
        return new ActionMessage("Tour rejected");
    }

    @Override @Transactional
    public TourManagementEquipment putEquipmentPackage(long accountId, long tourId, EquipmentPackage request) {
        Tour tour = ownedTour(tourId, accountId, true);
        var equipment = equipmentPackages.findByTourId(tourId).orElseGet(TourEquipmentPackage::new);
        equipment.setTourId(tourId); equipment.setTitle(request.title()); equipment.setIncludedItems(request.includedItems());
        equipment.setSharedItems(request.sharedItems()); equipment.setPersonalItemsToBring(request.personalItemsToBring());
        equipment = equipmentPackages.save(equipment);
        if (tour.getStatus() == TourStatus.PUBLISHED) {
            tour.setStatus(TourStatus.PENDING); tour.setReviewedBy(null); tour.setReviewedAt(null); tour.setReviewNote(null);
            tours.save(tour);
        }
        access.audit(accountId, "TOUR_EQUIPMENT_UPDATED", tourId, null);
        return new TourManagementEquipment(tourId, equipment.getTitle(), equipment.getIncludedItems(),
                equipment.getSharedItems(), equipment.getPersonalItemsToBring(), null);
    }

    @Override @Transactional
    public TourManagementSchedule createSchedule(long accountId, long tourId, ScheduleCreate r) {
        Tour tour = ownedTour(tourId, accountId, true);
        long companyId = tour.getCompanyId();
        validateSchedule(r.startsAt(), r.endsAt(), r.capacity(), r.balanceDueAt(), r.depositPercent());
        List<Long> guideIds = guideIds(r.guideAssignments());
        validateGuides(guideIds, companyId);
        TourSchedule s = new TourSchedule(); s.setTourId(tourId); s.setCompanyId(companyId); s.setStartsAt(r.startsAt()); s.setEndsAt(r.endsAt());
        s.setCapacity(r.capacity()); s.setBasePriceVnd(r.basePriceVnd()); s.setEquipmentFeeVnd(r.equipmentFeeVnd()); s.setDepositPercent(r.depositPercent());
        s.setBalanceDueAt(r.balanceDueAt()); s.setMeetingPoint(r.meetingPoint()); s.setContactPhone(r.contactPhone()); s.setStatus(ScheduleStatus.SCHEDULED);
        s = schedules.save(s); access.replaceScheduleGuides(s.getId(), companyId, guideIds);
        access.auditSchedule(accountId, "SCHEDULE_CREATED", s.getId(), null);
        return schedule(s);
    }

    @Override @Transactional
    public TourManagementSchedule patchSchedule(long accountId, long tourId, long scheduleId, SchedulePatch r) {
        Tour tour = ownedTour(tourId, accountId, true);
        TourSchedule s = ownedSchedule(tour, scheduleId);
        if (s.getStatus() != ScheduleStatus.SCHEDULED || s.getStartsAt().isBefore(OffsetDateTime.now(clock)))
            throw error(HttpStatus.CONFLICT, "SCHEDULE_STATE", "Only future scheduled departures can be changed");
        var starts = r.startsAt() == null ? s.getStartsAt() : r.startsAt();
        var ends = r.endsAt() == null ? s.getEndsAt() : r.endsAt();
        var capacity = r.capacity() == null ? s.getCapacity() : r.capacity();
        var balance = r.balanceDueAt() == null ? s.getBalanceDueAt() : r.balanceDueAt();
        var deposit = r.depositPercent() == null ? s.getDepositPercent() : r.depositPercent();
        validateSchedule(starts, ends, capacity, balance, deposit);
        if (capacity < access.countBookings(scheduleId)) throw error(HttpStatus.CONFLICT, "CAPACITY_OCCUPIED", "Capacity cannot be below held and confirmed seats");
        List<Long> guideIds = r.guideAssignments() == null ? null : guideIds(r.guideAssignments());
        if (guideIds != null) validateGuides(guideIds, tour.getCompanyId());
        boolean materialPriceChange = tour.getStatus() == TourStatus.PUBLISHED
                && ((r.basePriceVnd() != null && !r.basePriceVnd().equals(s.getBasePriceVnd()))
                || (r.equipmentFeeVnd() != null && !r.equipmentFeeVnd().equals(s.getEquipmentFeeVnd())));
        s.setStartsAt(starts); s.setEndsAt(ends); s.setCapacity(capacity);
        if (r.basePriceVnd() != null) s.setBasePriceVnd(r.basePriceVnd());
        if (r.equipmentFeeVnd() != null) s.setEquipmentFeeVnd(r.equipmentFeeVnd());
        s.setDepositPercent(deposit); s.setBalanceDueAt(balance);
        if (r.meetingPoint() != null) s.setMeetingPoint(r.meetingPoint()); if (r.contactPhone() != null) s.setContactPhone(r.contactPhone());
        s = schedules.save(s); if (guideIds != null) access.replaceScheduleGuides(scheduleId, tour.getCompanyId(), guideIds);
        if (materialPriceChange) {
            tour.setStatus(TourStatus.PENDING); tour.setReviewedBy(null); tour.setReviewedAt(null); tour.setReviewNote(null); tours.save(tour);
        }
        access.auditSchedule(accountId, "SCHEDULE_UPDATED", scheduleId, null); return schedule(s);
    }

    @Override @Transactional
    public ActionMessage cancelSchedule(long accountId, long tourId, long scheduleId, CancelSchedule request) {
        Tour tour = ownedTour(tourId, accountId, true); TourSchedule s = ownedSchedule(tour, scheduleId);
        if (s.getStatus() != ScheduleStatus.SCHEDULED && s.getStatus() != ScheduleStatus.ONGOING)
            throw error(HttpStatus.CONFLICT, "SCHEDULE_STATE", "Schedule cannot be cancelled in its current state");
        s.setStatus(ScheduleStatus.CANCELLED); s.setCancellationReason(request.reason().trim()); schedules.save(s);
        access.auditSchedule(accountId, "SCHEDULE_CANCELLED", scheduleId, request.reason().trim());
        return new ActionMessage("Schedule cancelled");
    }

    @Override @Transactional
    public ActionMessage completeSchedule(long accountId, long tourId, long scheduleId) {
        Tour tour = tours.findById(tourId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Tour not found"));
        TourSchedule s = ownedSchedule(tour, scheduleId);
        boolean manager = access.isActiveManager(tour.getCompanyId(), accountId);
        boolean guide = access.guideIdForAccount(accountId, tour.getCompanyId()).filter(id -> access.assignedGuide(scheduleId, id, tour.getCompanyId())).isPresent();
        if (!manager && !guide) throw error(HttpStatus.FORBIDDEN, "SCHEDULE_ACCESS", "Assigned guide or company manager access is required");
        if (s.getStatus() != ScheduleStatus.SCHEDULED && s.getStatus() != ScheduleStatus.ONGOING)
            throw error(HttpStatus.CONFLICT, "SCHEDULE_STATE", "Schedule cannot be completed in its current state");
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!s.getEndsAt().isBefore(now) || access.countUnresolvedHolds(scheduleId) > 0)
            throw error(HttpStatus.CONFLICT, "SCHEDULE_INCOMPLETE", "Departure must have ended and active payment holds must be resolved");
        s.setStatus(ScheduleStatus.COMPLETED); s.setActualCompletedAt(now); s.setCompletedBy(accountId); schedules.save(s);
        access.auditSchedule(accountId, "SCHEDULE_COMPLETED", scheduleId, null); return new ActionMessage("Schedule completed");
    }

    @Override @Transactional(readOnly = true)
    public RosterResponse roster(long accountId, long tourId, long scheduleId, int page, int size) {
        Tour tour = ownedTour(tourId, accountId, false); ownedSchedule(tour, scheduleId); validatePage(page, size);
        long total = access.rosterCount(scheduleId);
        return new RosterResponse(scheduleId, access.roster(scheduleId, tour.getCompanyId(), size, page * size),
                page, size, total, (int) Math.ceil((double) total / size));
    }

    @Override @Transactional(readOnly = true)
    public byte[] rosterXlsx(long accountId, long tourId, long scheduleId) {
        Tour tour = ownedTour(tourId, accountId, false); ownedSchedule(tour, scheduleId);
        var rows = access.roster(scheduleId, tour.getCompanyId(), 10_000, 0);
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Roster\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            xml.append(rowXml(1, List.of("Booking code", "Full name", "Phone", "Check-in time", "Check-in note")));
            for (int i = 0; i < rows.size(); i++) { var p = rows.get(i); xml.append(rowXml(i + 2, Arrays.asList(p.bookingCode(), p.fullName(), p.phone(),
                    p.checkedInAt() == null ? null : p.checkedInAt().toString(), p.checkInNote()))); }
            xml.append("</sheetData></worksheet>"); put(zip, "xl/worksheets/sheet1.xml", xml.toString()); zip.finish(); return bytes.toByteArray();
        } catch (IOException e) { throw error(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "Roster export could not be generated"); }
    }
    private void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private String rowXml(int row, List<String> values) {
        String[] columns = {"A", "B", "C", "D", "E"}; var xml = new StringBuilder("<row r=\"").append(row).append("\">");
        for (int i = 0; i < values.size(); i++) { String value = values.get(i); if (value == null) continue;
            xml.append("<c r=\"").append(columns[i]).append(row).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xmlEscape(value)).append("</t></is></c>"); }
        return xml.append("</row>").toString();
    }
    private String xmlEscape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }

    private void apply(Tour tour, TourWriteRequest r) {
        tour.setTitle(r.title()); tour.setDescription(r.description());
        tour.setDifficulty(r.difficulty() == null ? null : DifficultyLevel.valueOf(r.difficulty().toUpperCase(Locale.ROOT)));
        tour.setDurationDays(r.durationDays()); tour.setRequiredExperience(r.requiredExperience());
        tour.setIncludedServices(r.includedServices()); tour.setExcludedServices(r.excludedServices()); tour.setRouteFileUrl(r.routeFileUrl());
        if (r.cancellationPolicyVersionId() != null) {
            if (!access.validCancellationPolicy(r.cancellationPolicyVersionId(), tour.getCompanyId(), OffsetDateTime.now(clock)))
                throw error(HttpStatus.BAD_REQUEST, "INVALID_POLICY", "Cancellation policy is not valid for this company");
            tour.setCancellationPolicyVersionId(r.cancellationPolicyVersionId());
        }
    }

    private void replaceChildren(long tourId, List<Long> locationIds, List<MediaItem> mediaItems,
                                 List<ActivityItem> activityItems, List<com.trekking.app.features.tour.dto.request.PackingItem> packingItems) {
        replaceOptionalChildren(tourId, locationIds, mediaItems, activityItems, packingItems);
    }
    private void replaceOptionalChildren(long tourId, List<Long> locationIds, List<MediaItem> mediaItems,
                                         List<ActivityItem> activityItems, List<com.trekking.app.features.tour.dto.request.PackingItem> packingItems) {
        if (locationIds != null) {
            if (new HashSet<>(locationIds).size() != locationIds.size() || locations.findAllById(locationIds).size() != locationIds.size())
                throw error(HttpStatus.BAD_REQUEST, "INVALID_LOCATIONS", "One or more locations are invalid");
            tourLocations.deleteAllByTourId(tourId);
            for (int i = 0; i < locationIds.size(); i++) { TourLocation link = new TourLocation(); link.setTourId(tourId); link.setLocationId(locationIds.get(i)); link.setVisitOrder(i + 1); tourLocations.save(link); }
        }
        if (mediaItems != null) {
            if (mediaItems.stream().filter(MediaItem::cover).count() > 1) throw error(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "Only one cover image is allowed");
            media.deleteAllByTourId(tourId);
            for (var item : mediaItems) { TourMedia e = new TourMedia(); e.setTourId(tourId); e.setUrl(item.url()); e.setCaption(item.caption()); e.setDisplayOrder(item.displayOrder()); e.setIsCover(item.cover()); e.setMediaType(parseMediaType(item.mediaType())); media.save(e); }
        }
        if (activityItems != null) {
            List<Long> activityLocationIds = activityItems.stream().map(ActivityItem::locationId).filter(Objects::nonNull).distinct().toList();
            if (locations.findAllById(activityLocationIds).size() != activityLocationIds.size())
                throw error(HttpStatus.BAD_REQUEST, "INVALID_LOCATIONS", "One or more itinerary locations are invalid");
            activities.deleteAllByTourId(tourId);
            for (var item : activityItems) { TourActivity e = new TourActivity(); e.setTourId(tourId); e.setDayNumber(item.dayNumber()); e.setDisplayOrder(item.displayOrder()); e.setTitle(item.title()); e.setDescription(item.description()); e.setStartTime(item.startTime()); e.setLocationId(item.locationId()); activities.save(e); }
        }
        if (packingItems != null) {
            packing.deleteAllByTourId(tourId);
            for (var item : packingItems) { com.trekking.app.features.tour.entity.PackingItem e = new com.trekking.app.features.tour.entity.PackingItem(); e.setTourId(tourId); e.setName(item.name()); e.setIsRequired(item.required()); e.setNote(item.note()); e.setDisplayOrder(item.displayOrder()); packing.save(e); }
        }
    }

    private TourDetail detail(Tour t) {
        var locs = tourLocations.findAllByTourIdOrderByVisitOrderAsc(t.getId()).stream().map(TourLocation::getLocationId).toList();
        var mediaItems = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(t.getId()).stream().map(m -> new TourManagementMedia(m.getId(), m.getMediaType().getValue(), m.getUrl(), m.getCaption(), m.getDisplayOrder(), Boolean.TRUE.equals(m.getIsCover()))).toList();
        var acts = activities.findAllByTourIdOrderByDayNumberAscDisplayOrderAsc(t.getId()).stream().map(a -> new TourManagementActivity(a.getDayNumber(), a.getDisplayOrder(), a.getTitle(), a.getDescription(), a.getStartTime(), a.getLocationId())).toList();
        var pack = packing.findAllByTourIdOrderByDisplayOrderAscIdAsc(t.getId()).stream().map(p -> new TourManagementPacking(p.getId(), p.getName(), Boolean.TRUE.equals(p.getIsRequired()), p.getNote(), p.getDisplayOrder())).toList();
        var equipment = equipmentPackages.findByTourId(t.getId()).map(e -> List.of(new TourManagementEquipment(t.getId(), e.getTitle(), e.getIncludedItems(), e.getSharedItems(), e.getPersonalItemsToBring(), null))).orElseGet(List::of);
        var scheduleItems = schedules.findAllByTourIdOrderByStartsAtAsc(t.getId()).stream().map(this::schedule).toList();
        return new TourDetail(t.getId(), t.getCompanyId(), t.getTitle(), t.getDescription(), t.getStatus().getValue(), t.getDifficulty() == null ? null : t.getDifficulty().getValue(), t.getDurationDays(), t.getRequiredExperience(), t.getIncludedServices(), t.getExcludedServices(), t.getRouteFileUrl(), t.getCancellationPolicyVersionId(), t.getReviewNote(), locs, mediaItems, acts, pack, equipment, scheduleItems);
    }
    private TourItem item(Tour t) { return new TourItem(t.getId(), t.getCompanyId(), t.getTitle(), t.getStatus().getValue(), t.getDifficulty() == null ? null : t.getDifficulty().getValue(), t.getDurationDays(), t.getCreatedAt(), t.getUpdatedAt()); }
    private TourManagementSchedule schedule(TourSchedule s) {
        int available = s.getCapacity() - s.getHeldSeats() - s.getConfirmedSeats();
        return new TourManagementSchedule(s.getId(), s.getTourId(), s.getStartsAt(), s.getEndsAt(), s.getCapacity(),
                s.getHeldSeats(), s.getConfirmedSeats(), available, s.getBasePriceVnd(), s.getEquipmentFeeVnd(), s.getDepositPercent(),
                s.getBalanceDueAt(), s.getMeetingPoint(), s.getContactPhone(), s.getStatus().getValue(), access.scheduleGuides(s.getId()));
    }
    private TourSchedule ownedSchedule(Tour tour, long scheduleId) {
        return schedules.findByIdAndTourIdAndCompanyId(scheduleId, tour.getId(), tour.getCompanyId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule not found"));
    }
    private void validateSchedule(OffsetDateTime starts, OffsetDateTime ends, int capacity, OffsetDateTime balanceDue, java.math.BigDecimal deposit) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!starts.isAfter(now) || !ends.isAfter(starts) || !balanceDue.isBefore(starts) || capacity < 1
                || deposit == null || deposit.signum() <= 0 || deposit.compareTo(new java.math.BigDecimal("100")) > 0)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", "Schedule times, capacity, deposit and balance deadline are invalid");
    }
    private void validateGuides(List<Long> guideIds, long companyId) {
        if (guideIds == null) return;
        if (new HashSet<>(guideIds).size() != guideIds.size() || guideIds.stream().anyMatch(id -> !access.validActiveGuide(id, companyId)))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_GUIDES", "One or more assigned guides are not active members of this company");
    }
    private List<Long> guideIds(List<GuideAssignment> assignments) {
        if (assignments == null) return List.of();
        if (assignments.stream().filter(GuideAssignment::isLead).count() > 1)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_GUIDES", "At most one assigned guide can be lead");
        return assignments.stream().sorted(Comparator.comparing(GuideAssignment::isLead).reversed())
                .map(GuideAssignment::guideId).toList();
    }
    private Tour ownedTour(long tourId, long accountId, boolean manager) {
        long companyId = access.activeMemberCompanyForTour(tourId, accountId, manager).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Tour not found"));
        return tours.findByIdAndCompanyId(tourId, companyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Tour not found"));
    }
    private long singleCompany(long accountId) {
        var ids = access.activeManagerCompanies(accountId);
        if (ids.isEmpty()) throw error(HttpStatus.FORBIDDEN, "COMPANY_ACCESS", "Active company manager membership is required");
        if (ids.size() > 1) throw error(HttpStatus.CONFLICT, "COMPANY_AMBIGUOUS", "Account belongs to multiple managed companies");
        return ids.getFirst();
    }
    private Tour pending(long id) { return tours.findByIdAndStatus(id, TourStatus.PENDING).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TOUR_NOT_FOUND", "Pending tour not found")); }
    private void preventSelfReview(long adminId, long tourId) {
        Long submitter = access.lastSubmitter(tourId).orElseThrow(() -> error(HttpStatus.CONFLICT, "SUBMISSION_AUDIT_MISSING", "Tour submission audit is missing"));
        if (submitter == adminId) throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW", "A submitter cannot review their own tour");
    }
    private TourStatus parseStatus(String status) { try { return TourStatus.valueOf(status.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown tour status"); } }
    private MediaType parseMediaType(String type) { try { return MediaType.valueOf(type.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST, "INVALID_MEDIA", "Unknown media type"); } }
    private void validatePage(int page, int size) { if (page < 0 || page > 10_000 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be 0-10000 and size 1-100"); }
    private TourApiException error(HttpStatus status, String code, String message) { return new TourApiException(status, code, message); }
}
