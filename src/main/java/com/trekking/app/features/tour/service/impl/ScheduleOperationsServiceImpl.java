package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.entity.Guide;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.repository.GuideRepository;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.equipment.dto.response.EquipmentReservationResponse;
import com.trekking.app.features.equipment.entity.EquipmentHandoverItem;
import com.trekking.app.features.equipment.entity.EquipmentHandoverRecord;
import com.trekking.app.features.equipment.entity.EquipmentReservation;
import com.trekking.app.features.equipment.entity.EquipmentReservationItem;
import com.trekking.app.features.equipment.repository.EquipmentHandoverItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentHandoverRepository;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.storage.ImageStorage;
import com.trekking.app.features.tour.dto.request.ScheduleCheckInBatchRequest;
import com.trekking.app.features.tour.dto.request.ScheduleCheckOutBatchRequest;
import com.trekking.app.features.tour.dto.response.GuideAssignedScheduleResponse;
import com.trekking.app.features.tour.dto.response.HandoverPhotoUploadResponse;
import com.trekking.app.features.tour.dto.response.ScheduleEquipmentSummaryResponse;
import com.trekking.app.features.tour.dto.response.ScheduleGuideSummaryResponse;
import com.trekking.app.features.tour.dto.response.ScheduleManifestResponse;
import com.trekking.app.features.tour.dto.response.ScheduleOperationsReportResponse;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.entity.ParticipantEquipmentHandover;
import com.trekking.app.features.tour.entity.ScheduleAttendanceBatch;
import com.trekking.app.features.tour.entity.ScheduleGuide;
import com.trekking.app.features.tour.entity.ScheduleParticipantAttendance;
import com.trekking.app.features.tour.entity.TourParticipantCheckout;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.EquipmentRepository;
import com.trekking.app.features.tour.repository.ParticipantEquipmentHandoverRepository;
import com.trekking.app.features.tour.repository.ScheduleAttendanceBatchRepository;
import com.trekking.app.features.tour.repository.ScheduleGuideRepository;
import com.trekking.app.features.tour.repository.ScheduleParticipantAttendanceRepository;
import com.trekking.app.features.tour.repository.TourParticipantCheckoutRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.ScheduleOperationsService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class ScheduleOperationsServiceImpl implements ScheduleOperationsService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VIETNAM_ZONE);
    private static final List<BookingStatus> ACTIVE_TRIP_BOOKING_STATUSES =
            List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    private static final Set<String> VALID_CHECK_IN_STATUSES = Set.of("PRESENT", "ABSENT");
    private static final Set<String> VALID_CHECK_OUT_STATUSES =
            Set.of("COMPLETED", "EARLY_DEPARTURE", "ABSENT_AT_CHECKIN");
    private static final Set<String> VALID_EQUIPMENT_CONDITIONS =
            Set.of("GOOD", "NEEDS_INSPECTION", "DAMAGED", "MISSING");

    private final AuthorizationService authorization;
    private final TourScheduleRepository schedules;
    private final ScheduleGuideRepository scheduleGuides;
    private final GuideRepository guides;
    private final AppAccountRepository accounts;
    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final TourParticipantCheckoutRepository checkouts;
    private final EquipmentReservationRepository equipmentReservations;
    private final EquipmentReservationItemRepository equipmentReservationItems;
    private final EquipmentHandoverRepository equipmentHandovers;
    private final EquipmentHandoverItemRepository equipmentHandoverItems;
    private final EquipmentInventoryRepository equipmentInventory;
    private final EquipmentRepository equipmentCatalog;
    private final ScheduleAttendanceBatchRepository batches;
    private final ScheduleParticipantAttendanceRepository participantAttendances;
    private final ParticipantEquipmentHandoverRepository participantHandovers;
    private final ImageStorage imageStorage;
    private final ObjectMapper json;
    private final Clock clock;

    public ScheduleOperationsServiceImpl(
            AuthorizationService authorization,
            TourScheduleRepository schedules,
            ScheduleGuideRepository scheduleGuides,
            GuideRepository guides,
            AppAccountRepository accounts,
            BookingRepository bookings,
            ParticipantRepository participants,
            TourParticipantCheckoutRepository checkouts,
            EquipmentReservationRepository equipmentReservations,
            EquipmentReservationItemRepository equipmentReservationItems,
            EquipmentHandoverRepository equipmentHandovers,
            EquipmentHandoverItemRepository equipmentHandoverItems,
            EquipmentInventoryRepository equipmentInventory,
            EquipmentRepository equipmentCatalog,
            ScheduleAttendanceBatchRepository batches,
            ScheduleParticipantAttendanceRepository participantAttendances,
            ParticipantEquipmentHandoverRepository participantHandovers,
            ImageStorage imageStorage,
            ObjectMapper json,
            Clock clock) {
        this.authorization = authorization;
        this.schedules = schedules;
        this.scheduleGuides = scheduleGuides;
        this.guides = guides;
        this.accounts = accounts;
        this.bookings = bookings;
        this.participants = participants;
        this.checkouts = checkouts;
        this.equipmentReservations = equipmentReservations;
        this.equipmentReservationItems = equipmentReservationItems;
        this.equipmentHandovers = equipmentHandovers;
        this.equipmentHandoverItems = equipmentHandoverItems;
        this.equipmentInventory = equipmentInventory;
        this.equipmentCatalog = equipmentCatalog;
        this.batches = batches;
        this.participantAttendances = participantAttendances;
        this.participantHandovers = participantHandovers;
        this.imageStorage = imageStorage;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public List<GuideAssignedScheduleResponse> listMyAssignedSchedules() {
        var roles = authorization.currentRoles();
        if (!roles.contains(RoleType.GUIDE)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only guides can access assigned schedules");
        }
        long accountId = authorization.currentAccountId();
        var guideOpt = guides.findByAccountIdAndActiveTrue(accountId);
        if (guideOpt.isEmpty()) {
            return List.of();
        }
        Guide guide = guideOpt.get();

        List<ScheduleGuide> assignments = scheduleGuides.findAssignmentsWithScheduleByGuideId(guide.getId());
        if (assignments.isEmpty()) {
            return List.of();
        }

        List<Long> scheduleIds = assignments.stream().map(ScheduleGuide::getScheduleId).toList();
        List<Booking> activeBookings = bookings.findAllByScheduleIdInAndStatusInOrderByIdAsc(
                scheduleIds, ACTIVE_TRIP_BOOKING_STATUSES);

        Map<Long, Integer> passengerCountBySchedule = new HashMap<>();
        for (Booking booking : activeBookings) {
            passengerCountBySchedule.merge(
                    booking.getScheduleId(),
                    booking.getParticipantCount() == null ? 0 : booking.getParticipantCount(),
                    Integer::sum);
        }

        Map<String, ScheduleAttendanceBatch> batchMap = new HashMap<>();
        for (ScheduleAttendanceBatch batch : batches.findAllByScheduleIdIn(scheduleIds)) {
            batchMap.put(batch.getScheduleId() + ":" + batch.getRoundType(), batch);
        }

        return assignments.stream().map(assignment -> {
            TourSchedule s = assignment.getSchedule();
            ScheduleAttendanceBatch checkInBatch = batchMap.get(s.getId() + ":CHECK_IN");
            ScheduleAttendanceBatch checkOutBatch = batchMap.get(s.getId() + ":CHECK_OUT");
            int passengerCount = passengerCountBySchedule.getOrDefault(
                    s.getId(), s.getConfirmedSeats() == null ? 0 : s.getConfirmedSeats());

            return new GuideAssignedScheduleResponse(
                    s.getId(),
                    s.getTourId(),
                    s.getTour() != null ? s.getTour().getTitle() : "Chuyến Trekking",
                    s.getStartsAt(),
                    s.getEndsAt(),
                    s.getStatus() != null ? s.getStatus().name() : "SCHEDULED",
                    s.getMeetingPoint(),
                    s.getContactPhone(),
                    s.getCapacity() == null ? 0 : s.getCapacity(),
                    s.getConfirmedSeats() == null ? 0 : s.getConfirmedSeats(),
                    s.getHeldSeats() == null ? 0 : s.getHeldSeats(),
                    Boolean.TRUE.equals(assignment.getIsLead()),
                    assignment.getCreatedAt(),
                    passengerCount,
                    checkInBatch != null ? "CONFIRMED" : "NOT_STARTED",
                    checkInBatch != null ? checkInBatch.getSubmittedAt() : null,
                    checkOutBatch != null ? "CONFIRMED" : "NOT_STARTED",
                    checkOutBatch != null ? checkOutBatch.getSubmittedAt() : null
            );
        }).toList();
    }

    @Override
    public ScheduleManifestResponse getScheduleManifest(long scheduleId) {
        authorizeScheduleRead(scheduleId);
        TourSchedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule was not found"));

        List<ScheduleGuideSummaryResponse> assignedGuideDtos = scheduleGuides.findDetailsByScheduleId(scheduleId)
                .stream()
                .map(ag -> {
                    AppAccount acc = ag.getGuide().getAccount();
                    return new ScheduleGuideSummaryResponse(
                            ag.getGuideId(),
                            acc.getId(),
                            acc.getFullName(),
                            acc.getPhone(),
                            acc.getEmail(),
                            Boolean.TRUE.equals(ag.getIsLead()),
                            ag.getCreatedAt());
                })
                .toList();

        List<Booking> scheduleBookings = bookings.findAllByScheduleIdAndStatusInOrderByIdAsc(
                scheduleId, ACTIVE_TRIP_BOOKING_STATUSES);

        if (scheduleBookings.isEmpty()) {
            return new ScheduleManifestResponse(
                    schedule.getId(),
                    schedule.getTourId(),
                    schedule.getTour() != null ? schedule.getTour().getTitle() : "Chuyến Trekking",
                    schedule.getStartsAt(),
                    schedule.getEndsAt(),
                    schedule.getMeetingPoint(),
                    schedule.getContactPhone(),
                    schedule.getCapacity() == null ? 0 : schedule.getCapacity(),
                    schedule.getConfirmedSeats() == null ? 0 : schedule.getConfirmedSeats(),
                    schedule.getHeldSeats() == null ? 0 : schedule.getHeldSeats(),
                    assignedGuideDtos,
                    List.of()
            );
        }

        List<Long> bookingIds = scheduleBookings.stream().map(Booking::getId).toList();
        Set<Long> customerIds = scheduleBookings.stream().map(Booking::getCustomerId).collect(Collectors.toSet());
        Map<Long, AppAccount> customerMap = accounts.findAllById(customerIds).stream()
                .collect(Collectors.toMap(AppAccount::getId, Function.identity()));

        List<Participant> allParticipants = participants.findAllByBookingIdInOrderByIdAsc(bookingIds);
        List<Long> participantIds = allParticipants.stream().map(Participant::getId).toList();
        Map<Long, TourParticipantCheckout> checkoutMap = participantIds.isEmpty()
                ? Map.of()
                : checkouts.findAllById(participantIds).stream()
                        .collect(Collectors.toMap(TourParticipantCheckout::getParticipantId, Function.identity()));

        Map<Long, List<Participant>> participantsByBooking = allParticipants.stream()
                .collect(Collectors.groupingBy(Participant::getBookingId, LinkedHashMap::new, Collectors.toList()));

        List<EquipmentReservation> reservations = equipmentReservations.findAllByScheduleIdOrderByIdAsc(scheduleId);
        List<Long> reservationIds = reservations.stream().map(EquipmentReservation::getId).toList();
        Map<Long, List<EquipmentReservationItem>> itemsByReservation = reservationIds.isEmpty()
                ? Map.of()
                : equipmentReservationItems.findAllByReservationIdInOrderByIdAsc(reservationIds).stream()
                        .collect(Collectors.groupingBy(
                                EquipmentReservationItem::getReservationId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, EquipmentReservation> reservationByBooking = reservations.stream()
                .collect(Collectors.toMap(EquipmentReservation::getBookingId, Function.identity(), (a, b) -> a));

        List<ScheduleManifestResponse.ManifestBookingItem> bookingItems = scheduleBookings.stream().map(booking -> {
            AppAccount customer = customerMap.get(booking.getCustomerId());
            List<Participant> bookingParticipants = participantsByBooking.getOrDefault(booking.getId(), List.of());

            List<ScheduleManifestResponse.ManifestParticipantItem> passengerItems = bookingParticipants.stream()
                    .map(p -> {
                        TourParticipantCheckout co = checkoutMap.get(p.getId());
                        boolean isBookingCustomer = p.getAccountId() != null
                                && p.getAccountId().equals(booking.getCustomerId());
                        return new ScheduleManifestResponse.ManifestParticipantItem(
                                p.getId(),
                                booking.getId(),
                                booking.getBookingCode(),
                                p.getFullName(),
                                p.getPhone(),
                                p.getEmail(),
                                isBookingCustomer,
                                p.getCheckedInAt() != null,
                                p.getCheckedInAt(),
                                p.getCheckedInBy(),
                                co != null,
                                co != null ? co.getCheckedOutAt() : null,
                                co != null ? co.getCheckedOutBy() : null,
                                p.getCheckInNote()
                        );
                    })
                    .toList();

            EquipmentReservation res = reservationByBooking.get(booking.getId());
            EquipmentReservationResponse equipmentDto = null;
            if (res != null && !"released".equalsIgnoreCase(res.getStatus())) {
                List<EquipmentReservationResponse.Item> eqItems = itemsByReservation
                        .getOrDefault(res.getId(), List.of())
                        .stream()
                        .map(item -> new EquipmentReservationResponse.Item(
                                item.getEquipmentId(),
                                item.getEquipment() != null ? item.getEquipment().getName() : "Thiết bị #" + item.getEquipmentId(),
                                item.getQuantity(),
                                item.getIssuedQuantity(),
                                item.getReturnedQuantity()))
                        .toList();
                equipmentDto = new EquipmentReservationResponse(
                        res.getId(),
                        res.getBookingId(),
                        res.getScheduleId(),
                        res.getPackageId(),
                        res.getStatus(),
                        res.getHoldExpiresAt(),
                        eqItems
                );
            }

            String fallbackCustomerName = !passengerItems.isEmpty()
                    ? passengerItems.stream()
                            .filter(ScheduleManifestResponse.ManifestParticipantItem::bookingCustomer)
                            .map(ScheduleManifestResponse.ManifestParticipantItem::fullName)
                            .findFirst()
                            .orElse(passengerItems.get(0).fullName())
                    : "Khách đặt chỗ";

            return new ScheduleManifestResponse.ManifestBookingItem(
                    booking.getId(),
                    booking.getBookingCode(),
                    customer != null ? customer.getFullName() : fallbackCustomerName,
                    customer != null ? customer.getPhone() : null,
                    customer != null ? customer.getEmail() : null,
                    booking.getStatus().name(),
                    passengerItems,
                    equipmentDto
            );
        }).toList();

        return new ScheduleManifestResponse(
                schedule.getId(),
                schedule.getTourId(),
                schedule.getTour() != null ? schedule.getTour().getTitle() : "Chuyến Trekking",
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                schedule.getMeetingPoint(),
                schedule.getContactPhone(),
                schedule.getCapacity() == null ? 0 : schedule.getCapacity(),
                schedule.getConfirmedSeats() == null ? 0 : schedule.getConfirmedSeats(),
                schedule.getHeldSeats() == null ? 0 : schedule.getHeldSeats(),
                assignedGuideDtos,
                bookingItems
        );
    }

    @Override
    public ScheduleEquipmentSummaryResponse getScheduleEquipmentSummary(long scheduleId) {
        authorizeScheduleRead(scheduleId);
        if (!schedules.existsById(scheduleId)) {
            throw new TourException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule was not found");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Booking> confirmedBookings = bookings.findAllByScheduleIdAndStatusInOrderByIdAsc(
                scheduleId, ACTIVE_TRIP_BOOKING_STATUSES);
        List<EquipmentReservation> reservations = equipmentReservations.findAllByScheduleIdOrderByIdAsc(scheduleId);

        Set<Long> confirmedBookingIds = confirmedBookings.stream().map(Booking::getId).collect(Collectors.toSet());
        int bookingsWithEquipment = 0;
        int activeHoldBookingsWithEquipment = 0;
        for (EquipmentReservation r : reservations) {
            String st = r.getStatus() == null ? "" : r.getStatus().toLowerCase();
            if (confirmedBookingIds.contains(r.getBookingId())
                    && Set.of("confirmed", "issued", "return_required", "returned").contains(st)) {
                bookingsWithEquipment++;
            } else if ("held".equals(st) && r.getHoldExpiresAt() != null && r.getHoldExpiresAt().isAfter(now)) {
                activeHoldBookingsWithEquipment++;
            }
        }

        var rows = equipmentReservations.summarizeScheduleEquipment(scheduleId, now);
        int totalOrdered = 0;
        int totalIssued = 0;
        int totalReturned = 0;
        int totalActiveHold = 0;
        List<ScheduleEquipmentSummaryResponse.ScheduleEquipmentSummaryItem> items = new ArrayList<>();

        for (var row : rows) {
            int ordered = row.getOrderedQuantity() == null ? 0 : row.getOrderedQuantity().intValue();
            int issued = row.getIssuedQuantity() == null ? 0 : row.getIssuedQuantity().intValue();
            int returned = row.getReturnedQuantity() == null ? 0 : row.getReturnedQuantity().intValue();
            int bCount = row.getBookingCount() == null ? 0 : row.getBookingCount().intValue();
            int holdQty = row.getActiveHoldQuantity() == null ? 0 : row.getActiveHoldQuantity().intValue();
            int holdBCount = row.getActiveHoldBookingCount() == null ? 0 : row.getActiveHoldBookingCount().intValue();

            totalOrdered += ordered;
            totalIssued += issued;
            totalReturned += returned;
            totalActiveHold += holdQty;

            items.add(new ScheduleEquipmentSummaryResponse.ScheduleEquipmentSummaryItem(
                    row.getEquipmentId(),
                    row.getEquipmentName(),
                    ordered,
                    issued,
                    returned,
                    bCount,
                    holdQty,
                    holdBCount
            ));
        }

        return new ScheduleEquipmentSummaryResponse(
                scheduleId,
                confirmedBookings.size(),
                bookingsWithEquipment,
                activeHoldBookingsWithEquipment,
                totalOrdered,
                totalIssued,
                totalReturned,
                totalActiveHold,
                items
        );
    }

    @Override
    public HandoverPhotoUploadResponse uploadHandoverPhoto(long scheduleId, MultipartFile file) {
        authorization.requireGuideSchedule(scheduleId);
        if (file == null || file.isEmpty()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn ảnh hiện trạng thiết bị.");
        }
        OffsetDateTime capturedAt = OffsetDateTime.now(clock);
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "handover.jpg";
        String id = UUID.randomUUID().toString();
        String folder = "trekking/schedules/" + scheduleId + "/handover";

        try {
            var stored = imageStorage.upload(file, folder);
            return new HandoverPhotoUploadResponse(
                    id,
                    stored.url(),
                    stored.key(),
                    originalName,
                    capturedAt
            );
        } catch (Exception ex) {
            try {
                String mime = file.getContentType() != null ? file.getContentType() : "image/jpeg";
                String base64 = Base64.getEncoder().encodeToString(file.getBytes());
                String dataUrl = "data:" + mime + ";base64," + base64;
                String storageKey = folder + "/" + id + "-" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
                return new HandoverPhotoUploadResponse(id, dataUrl, storageKey, originalName, capturedAt);
            } catch (Exception readEx) {
                throw new TourException(HttpStatus.BAD_REQUEST, "PHOTO_UPLOAD_FAILED", "Không thể đọc tệp ảnh bàn giao.");
            }
        }
    }

    @Override
    public ScheduleOperationsReportResponse getOperationsReport(long scheduleId) {
        authorizeScheduleRead(scheduleId);
        TourSchedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule was not found"));

        return buildOperationsReportResponse(schedule);
    }

    @Override
    @Transactional
    public ScheduleOperationsReportResponse.CheckInBatchReportResponse submitCheckInBatch(
            long scheduleId, ScheduleCheckInBatchRequest request) {
        authorization.requireGuideSchedule(scheduleId);
        long actorAccountId = authorization.currentAccountId();
        Guide guide = guides.findByAccountIdAndActiveTrue(actorAccountId)
                .orElseThrow(() -> new AuthException(HttpStatus.FORBIDDEN, "GUIDE_NOT_ACTIVE", "Guide is not active"));

        TourSchedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule was not found"));

        if (batches.findByScheduleIdAndRoundType(scheduleId, "CHECK_IN").isPresent()) {
            throw new TourException(
                    HttpStatus.CONFLICT,
                    "CHECK_IN_BATCH_ALREADY_CONFIRMED",
                    "Lượt check-in của lịch khởi hành này đã được xác nhận hoàn tất trước đó, không thể gửi trùng.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime checkInUnlockAt = schedule.getStartsAt().minusHours(1);
        if (now.isBefore(checkInUnlockAt)) {
            throw new TourException(
                    HttpStatus.BAD_REQUEST,
                    "CHECK_IN_WINDOW_NOT_OPEN",
                    "Chưa đến khung giờ check-in. Chỉ được bắt đầu và xác nhận check-in từ đúng 1 giờ trước giờ khởi hành ("
                            + VN_FORMATTER.format(checkInUnlockAt) + " giờ Việt Nam).");
        }

        List<Booking> activeBookings = bookings.findAllByScheduleIdAndStatusInOrderByIdAsc(
                scheduleId, ACTIVE_TRIP_BOOKING_STATUSES);
        if (activeBookings.isEmpty()) {
            throw new TourException(
                    HttpStatus.BAD_REQUEST,
                    "NO_CONFIRMED_BOOKINGS",
                    "Lịch khởi hành chưa có đơn đặt chỗ xác nhận nào để check-in.");
        }

        Map<Long, Booking> bookingMap = activeBookings.stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        List<Participant> scheduleParticipants = participants.findAllByBookingIdInOrderByIdAsc(
                new ArrayList<>(bookingMap.keySet()));
        Map<Long, Participant> participantMap = scheduleParticipants.stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));

        Map<Long, ScheduleCheckInBatchRequest.ParticipantCheckInItemRequest> submittedByParticipantId =
                new LinkedHashMap<>();
        for (var item : request.participants()) {
            Participant p = participantMap.get(item.participantId());
            if (p == null || !p.getBookingId().equals(item.bookingId())) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTICIPANT",
                        "Hành khách #" + item.participantId() + " không thuộc đơn đặt chỗ của lịch khởi hành này.");
            }
            String status = item.status() == null ? "" : item.status().trim().toUpperCase();
            if (!VALID_CHECK_IN_STATUSES.contains(status)) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "PARTICIPANT_CHECK_IN_INCOMPLETE",
                        "Hành khách \"" + p.getFullName() + "\" chưa được đánh dấu Có mặt (PRESENT) hoặc Vắng mặt (ABSENT).");
            }
            if (submittedByParticipantId.put(item.participantId(), item) != null) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_PARTICIPANT_IN_BATCH",
                        "Hành khách \"" + p.getFullName() + "\" bị trùng lặp trong danh sách gửi.");
            }
        }

        for (Participant p : scheduleParticipants) {
            if (!submittedByParticipantId.containsKey(p.getId())) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "MISSING_PARTICIPANT_IN_BATCH",
                        "Còn hành khách \"" + p.getFullName() + "\" chưa được xử lý trong danh sách check-in.");
            }
        }

        // Load equipment reservations for bookings on this schedule
        List<EquipmentReservation> reservations = equipmentReservations.findAllByScheduleIdOrderByIdAsc(scheduleId);
        Map<Long, EquipmentReservation> reservationByBookingId = reservations.stream()
                .collect(Collectors.toMap(EquipmentReservation::getBookingId, Function.identity(), (a, b) -> a));
        List<Long> reservationIds = reservations.stream().map(EquipmentReservation::getId).toList();
        Map<Long, List<EquipmentReservationItem>> resItemsByReservationId = reservationIds.isEmpty()
                ? Map.of()
                : equipmentReservationItems.findAllByReservationIdInOrderByIdAsc(reservationIds).stream()
                        .collect(Collectors.groupingBy(EquipmentReservationItem::getReservationId));

        Map<Long, Map<Long, Integer>> allocatedByBookingAndEquipment = new HashMap<>();

        for (var item : request.participants()) {
            Participant p = participantMap.get(item.participantId());
            List<ScheduleCheckInBatchRequest.EquipmentCheckInItemRequest> eqItems =
                    item.equipmentItems() == null ? List.of() : item.equipmentItems();
            for (var eq : eqItems) {
                if (eq.allocatedQuantity() == null || eq.allocatedQuantity() < 0) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_EQUIPMENT_QUANTITY",
                            "Số lượng thiết bị bàn giao cho khách \"" + p.getFullName() + "\" không hợp lệ.");
                }
                if (eq.allocatedQuantity() == 0) continue;

                String cond = eq.condition() == null ? "GOOD" : eq.condition().trim().toUpperCase();
                if (!VALID_EQUIPMENT_CONDITIONS.contains(cond)) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_EQUIPMENT_CONDITION",
                            "Tình trạng thiết bị bàn giao cho khách \"" + p.getFullName() + "\" không hợp lệ.");
                }
                if (("DAMAGED".equals(cond) || "MISSING".equals(cond))
                        && (eq.note() == null || eq.note().isBlank())) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "EQUIPMENT_NOTE_REQUIRED",
                            "Vui lòng nhập ghi chú cho thiết bị hỏng/thiếu của khách \"" + p.getFullName() + "\".");
                }

                allocatedByBookingAndEquipment
                        .computeIfAbsent(p.getBookingId(), k -> new HashMap<>())
                        .merge(eq.equipmentId(), eq.allocatedQuantity(), Integer::sum);
            }
        }

        // Validate total allocated per booking does not exceed booking's reserved quantity
        for (var bookingEntry : allocatedByBookingAndEquipment.entrySet()) {
            long bookingId = bookingEntry.getKey();
            EquipmentReservation res = reservationByBookingId.get(bookingId);
            Map<Long, EquipmentReservationItem> reservedMap = res == null
                    ? Map.of()
                    : resItemsByReservationId.getOrDefault(res.getId(), List.of()).stream()
                            .collect(Collectors.toMap(EquipmentReservationItem::getEquipmentId, Function.identity()));

            for (var eqEntry : bookingEntry.getValue().entrySet()) {
                long equipmentId = eqEntry.getKey();
                int totalAllocated = eqEntry.getValue();
                EquipmentReservationItem reservedItem = reservedMap.get(equipmentId);
                if (reservedItem == null) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "EQUIPMENT_NOT_IN_BOOKING",
                            "Thiết bị #" + equipmentId + " không nằm trong danh sách thuê của đơn #" + bookingId + ".");
                }
                if (totalAllocated > reservedItem.getQuantity()) {
                    String eqName = reservedItem.getEquipment() != null
                            ? reservedItem.getEquipment().getName()
                            : "#" + equipmentId;
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "EQUIPMENT_ALLOCATION_EXCEEDS_BOOKING",
                            "Tổng số lượng bàn giao thiết bị \"" + eqName + "\" (" + totalAllocated
                                    + ") vượt quá số lượng đặt của đơn (" + reservedItem.getQuantity() + ").");
                }
            }
        }

        // Save batch record
        ScheduleAttendanceBatch batch = new ScheduleAttendanceBatch();
        batch.setScheduleId(scheduleId);
        batch.setRoundType("CHECK_IN");
        batch.setRoundStatus("CONFIRMED");
        batch.setIdempotencyKey(request.idempotencyKey().trim());
        batch.setStartedAt(request.startedAt() != null ? request.startedAt() : now);
        batch.setSubmittedAt(now);
        batch.setSubmittedByAccountId(actorAccountId);
        batch.setSubmittedByGuideId(guide.getId());
        batch.setGeneralNote(blankToNull(request.generalNote()));
        ScheduleAttendanceBatch savedBatch = batches.save(batch);

        // Save participant attendance records & participant equipment handovers
        for (var item : request.participants()) {
            Participant p = participantMap.get(item.participantId());
            String status = item.status().trim().toUpperCase();
            OffsetDateTime markedAt = item.markedAt() != null ? item.markedAt() : now;
            String note = blankToNull(item.note());

            ScheduleParticipantAttendance attendance = participantAttendances.findByParticipantId(p.getId())
                    .orElseGet(ScheduleParticipantAttendance::new);
            attendance.setScheduleId(scheduleId);
            attendance.setBookingId(p.getBookingId());
            attendance.setParticipantId(p.getId());
            attendance.setCheckInBatchId(savedBatch.getId());
            attendance.setCheckInStatus(status);
            attendance.setCheckInMarkedAt(markedAt);
            attendance.setCheckInNote(note);
            participantAttendances.save(attendance);

            if ("PRESENT".equals(status) && p.getCheckedInAt() == null) {
                p.setCheckedInAt(markedAt);
                p.setCheckedInBy(actorAccountId);
                p.setCheckInNote(note);
                participants.save(p);
            }

            List<ScheduleCheckInBatchRequest.EquipmentCheckInItemRequest> eqItems =
                    item.equipmentItems() == null ? List.of() : item.equipmentItems();
            for (var eq : eqItems) {
                if (eq.allocatedQuantity() == null || eq.allocatedQuantity() <= 0) continue;
                ParticipantEquipmentHandover handover = participantHandovers
                        .findByScheduleIdAndParticipantIdAndEquipmentId(scheduleId, p.getId(), eq.equipmentId())
                        .orElseGet(ParticipantEquipmentHandover::new);
                handover.setScheduleId(scheduleId);
                handover.setBookingId(p.getBookingId());
                handover.setParticipantId(p.getId());
                handover.setEquipmentId(eq.equipmentId());
                handover.setAllocatedQuantity(eq.allocatedQuantity());
                handover.setCheckInCondition(eq.condition().trim().toUpperCase());
                handover.setCheckInNote(blankToNull(eq.note()));
                handover.setCheckInPhotosJson(serializePhotos(eq.photos()));
                handover.setHandedOverAt(eq.handedOverAt() != null ? eq.handedOverAt() : now);
                participantHandovers.save(handover);
            }
        }

        // Sync booking-level equipment reservation issuedQuantity & handover history
        for (var bookingEntry : allocatedByBookingAndEquipment.entrySet()) {
            long bookingId = bookingEntry.getKey();
            EquipmentReservation res = reservationByBookingId.get(bookingId);
            if (res == null) continue;
            List<EquipmentReservationItem> lines = resItemsByReservationId.getOrDefault(res.getId(), List.of());

            EquipmentHandoverRecord record = new EquipmentHandoverRecord();
            record.setReservationId(res.getId());
            record.setAction("issued");
            record.setActorId(actorAccountId);
            record.setNote(blankToNull(request.generalNote()));
            record.setOccurredAt(now);
            EquipmentHandoverRecord savedRecord = equipmentHandovers.save(record);

            for (EquipmentReservationItem line : lines) {
                int allocatedTotal = bookingEntry.getValue().getOrDefault(line.getEquipmentId(), 0);
                if (allocatedTotal > 0) {
                    int newIssued = Math.min(line.getQuantity(), Math.max(line.getIssuedQuantity(), allocatedTotal));
                    int delta = newIssued - line.getIssuedQuantity();
                    line.setIssuedQuantity(newIssued);
                    equipmentReservationItems.save(line);
                    if (delta > 0) {
                        EquipmentHandoverItem hi = new EquipmentHandoverItem();
                        hi.setHandoverRecordId(savedRecord.getId());
                        hi.setEquipmentId(line.getEquipmentId());
                        hi.setQuantity(delta);
                        hi.setCondition("good");
                        equipmentHandoverItems.save(hi);
                    }
                }
            }
            boolean fullyIssued = lines.stream().allMatch(l -> l.getIssuedQuantity() >= l.getQuantity());
            if (fullyIssued && "confirmed".equalsIgnoreCase(res.getStatus())) {
                res.setStatus("issued");
                equipmentReservations.save(res);
            }
        }

        return buildOperationsReportResponse(schedule).checkInReport();
    }

    @Override
    @Transactional
    public ScheduleOperationsReportResponse.CheckOutBatchReportResponse submitCheckOutBatch(
            long scheduleId, ScheduleCheckOutBatchRequest request) {
        authorization.requireGuideSchedule(scheduleId);
        long actorAccountId = authorization.currentAccountId();
        Guide guide = guides.findByAccountIdAndActiveTrue(actorAccountId)
                .orElseThrow(() -> new AuthException(HttpStatus.FORBIDDEN, "GUIDE_NOT_ACTIVE", "Guide is not active"));

        TourSchedule schedule = schedules.findById(scheduleId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule was not found"));

        if (batches.findByScheduleIdAndRoundType(scheduleId, "CHECK_IN").isEmpty()) {
            throw new TourException(
                    HttpStatus.BAD_REQUEST,
                    "CHECK_IN_REQUIRED_BEFORE_CHECK_OUT",
                    "Bạn cần hoàn tất và xác nhận lượt Check-in trước khi thực hiện xác nhận lượt Check-out.");
        }

        if (batches.findByScheduleIdAndRoundType(scheduleId, "CHECK_OUT").isPresent()) {
            throw new TourException(
                    HttpStatus.CONFLICT,
                    "CHECK_OUT_BATCH_ALREADY_CONFIRMED",
                    "Lượt check-out của lịch khởi hành này đã được xác nhận hoàn tất trước đó, không thể gửi trùng.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (now.isBefore(schedule.getEndsAt())) {
            throw new TourException(
                    HttpStatus.BAD_REQUEST,
                    "CHECK_OUT_WINDOW_NOT_OPEN",
                    "Chưa đến giờ kết thúc chuyến (" + VN_FORMATTER.format(schedule.getEndsAt())
                            + " giờ Việt Nam). Chỉ được phép check-out khi chuyến đã đến giờ kết thúc.");
        }

        List<ScheduleParticipantAttendance> existingAttendances =
                participantAttendances.findAllByScheduleIdOrderByIdAsc(scheduleId);
        Map<Long, ScheduleParticipantAttendance> attendanceByParticipantId = existingAttendances.stream()
                .collect(Collectors.toMap(ScheduleParticipantAttendance::getParticipantId, Function.identity()));

        List<Booking> activeBookings = bookings.findAllByScheduleIdAndStatusInOrderByIdAsc(
                scheduleId, ACTIVE_TRIP_BOOKING_STATUSES);
        List<Participant> scheduleParticipants = participants.findAllByBookingIdInOrderByIdAsc(
                activeBookings.stream().map(Booking::getId).toList());
        Map<Long, Participant> participantMap = scheduleParticipants.stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));

        List<ParticipantEquipmentHandover> existingHandovers =
                participantHandovers.findAllByScheduleIdOrderByIdAsc(scheduleId);
        Map<String, ParticipantEquipmentHandover> handoverByParticipantAndEquipment = existingHandovers.stream()
                .collect(Collectors.toMap(
                        h -> h.getParticipantId() + ":" + h.getEquipmentId(), Function.identity()));

        Map<Long, ScheduleCheckOutBatchRequest.ParticipantCheckOutItemRequest> submittedByParticipantId =
                new LinkedHashMap<>();
        for (var item : request.participants()) {
            Participant p = participantMap.get(item.participantId());
            if (p == null || !p.getBookingId().equals(item.bookingId())) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTICIPANT",
                        "Hành khách #" + item.participantId() + " không thuộc lịch khởi hành này.");
            }
            String status = item.status() == null ? "" : item.status().trim().toUpperCase();
            if (!VALID_CHECK_OUT_STATUSES.contains(status)) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "PARTICIPANT_CHECK_OUT_INCOMPLETE",
                        "Hành khách \"" + p.getFullName() + "\" chưa được chọn trạng thái kết thúc chuyến.");
            }
            submittedByParticipantId.put(p.getId(), item);
        }

        for (Participant p : scheduleParticipants) {
            if (!submittedByParticipantId.containsKey(p.getId())) {
                throw new TourException(
                        HttpStatus.BAD_REQUEST,
                        "MISSING_PARTICIPANT_IN_BATCH",
                        "Còn hành khách \"" + p.getFullName() + "\" chưa được xử lý trong danh sách check-out.");
            }
        }

        Map<Long, Map<Long, Integer>> returnedByBookingAndEquipment = new HashMap<>();

        // Validate equipment returns against issued quantities
        for (var item : request.participants()) {
            Participant p = participantMap.get(item.participantId());
            List<ScheduleCheckOutBatchRequest.EquipmentCheckOutItemRequest> eqItems =
                    item.equipmentItems() == null ? List.of() : item.equipmentItems();

            for (var eq : eqItems) {
                ParticipantEquipmentHandover handover =
                        handoverByParticipantAndEquipment.get(p.getId() + ":" + eq.equipmentId());
                if (handover == null) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "EQUIPMENT_NOT_ISSUED_TO_PARTICIPANT",
                            "Thiết bị #" + eq.equipmentId() + " chưa từng được bàn giao cho khách \"" + p.getFullName() + "\" lúc check-in.");
                }
                int issuedQty = handover.getAllocatedQuantity();
                int returnedQty = eq.returnedQuantity() == null ? -1 : eq.returnedQuantity();
                if (returnedQty < 0 || returnedQty > issuedQty) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "EQUIPMENT_RETURN_EXCEEDS_ISSUED",
                            "Khách \"" + p.getFullName() + "\": Số lượng thiết bị nhận lại (" + returnedQty
                                    + ") không được vượt quá số lượng đã bàn giao (" + issuedQty + ").");
                }
                String cond = eq.condition() == null ? "GOOD" : eq.condition().trim().toUpperCase();
                if (!VALID_EQUIPMENT_CONDITIONS.contains(cond)) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_EQUIPMENT_CONDITION",
                            "Tình trạng thiết bị nhận lại của khách \"" + p.getFullName() + "\" không hợp lệ.");
                }
                boolean isShortage = returnedQty < issuedQty;
                boolean isDamagedOrMissing = !"GOOD".equals(cond);
                if ((isShortage || isDamagedOrMissing) && (eq.note() == null || eq.note().isBlank())) {
                    throw new TourException(
                            HttpStatus.BAD_REQUEST,
                            "EQUIPMENT_RETURN_NOTE_REQUIRED",
                            "Vui lòng nhập ghi chú tình trạng thiết bị thiếu/hỏng cho khách \"" + p.getFullName() + "\".");
                }

                returnedByBookingAndEquipment
                        .computeIfAbsent(p.getBookingId(), k -> new HashMap<>())
                        .merge(eq.equipmentId(), returnedQty, Integer::sum);
            }
        }

        ScheduleAttendanceBatch batch = new ScheduleAttendanceBatch();
        batch.setScheduleId(scheduleId);
        batch.setRoundType("CHECK_OUT");
        batch.setRoundStatus("CONFIRMED");
        batch.setIdempotencyKey(request.idempotencyKey().trim());
        batch.setStartedAt(request.startedAt() != null ? request.startedAt() : now);
        batch.setSubmittedAt(now);
        batch.setSubmittedByAccountId(actorAccountId);
        batch.setSubmittedByGuideId(guide.getId());
        batch.setGeneralNote(blankToNull(request.generalNote()));
        ScheduleAttendanceBatch savedBatch = batches.save(batch);

        for (var item : request.participants()) {
            Participant p = participantMap.get(item.participantId());
            String status = item.status().trim().toUpperCase();
            OffsetDateTime markedAt = item.markedAt() != null ? item.markedAt() : now;
            String note = blankToNull(item.note());

            ScheduleParticipantAttendance attendance = attendanceByParticipantId.get(p.getId());
            if (attendance != null) {
                attendance.setCheckOutBatchId(savedBatch.getId());
                attendance.setCheckOutStatus(status);
                attendance.setCheckOutMarkedAt(markedAt);
                attendance.setCheckOutNote(note);
                participantAttendances.save(attendance);
            }

            if (!"ABSENT_AT_CHECKIN".equals(status) && p.getCheckedInAt() != null && !checkouts.existsById(p.getId())) {
                TourParticipantCheckout co = new TourParticipantCheckout();
                co.setParticipantId(p.getId());
                co.setCheckedOutAt(markedAt);
                co.setCheckedOutBy(actorAccountId);
                co.setNote(note);
                checkouts.save(co);
            }

            List<ScheduleCheckOutBatchRequest.EquipmentCheckOutItemRequest> eqItems =
                    item.equipmentItems() == null ? List.of() : item.equipmentItems();
            for (var eq : eqItems) {
                ParticipantEquipmentHandover handover =
                        handoverByParticipantAndEquipment.get(p.getId() + ":" + eq.equipmentId());
                if (handover == null) continue;
                handover.setReturnedQuantity(eq.returnedQuantity());
                handover.setCheckOutCondition(eq.condition().trim().toUpperCase());
                handover.setCheckOutNote(blankToNull(eq.note()));
                handover.setCheckOutPhotosJson(serializePhotos(eq.photos()));
                handover.setReturnedAt(eq.returnedAt() != null ? eq.returnedAt() : now);
                participantHandovers.save(handover);
            }
        }

        // Sync booking-level equipment reservation returnedQuantity
        List<EquipmentReservation> reservations = equipmentReservations.findAllByScheduleIdOrderByIdAsc(scheduleId);
        Map<Long, EquipmentReservation> reservationByBookingId = reservations.stream()
                .collect(Collectors.toMap(EquipmentReservation::getBookingId, Function.identity(), (a, b) -> a));
        List<Long> reservationIds = reservations.stream().map(EquipmentReservation::getId).toList();
        Map<Long, List<EquipmentReservationItem>> resItemsByReservationId = reservationIds.isEmpty()
                ? Map.of()
                : equipmentReservationItems.findAllByReservationIdInOrderByIdAsc(reservationIds).stream()
                        .collect(Collectors.groupingBy(EquipmentReservationItem::getReservationId));

        for (var bookingEntry : returnedByBookingAndEquipment.entrySet()) {
            long bookingId = bookingEntry.getKey();
            EquipmentReservation res = reservationByBookingId.get(bookingId);
            if (res == null) continue;
            List<EquipmentReservationItem> lines = resItemsByReservationId.getOrDefault(res.getId(), List.of());

            EquipmentHandoverRecord record = new EquipmentHandoverRecord();
            record.setReservationId(res.getId());
            record.setAction("returned");
            record.setActorId(actorAccountId);
            record.setNote(blankToNull(request.generalNote()));
            record.setOccurredAt(now);
            EquipmentHandoverRecord savedRecord = equipmentHandovers.save(record);

            for (EquipmentReservationItem line : lines) {
                int returnedTotal = bookingEntry.getValue().getOrDefault(line.getEquipmentId(), 0);
                if (returnedTotal > 0) {
                    int newReturned = Math.min(line.getIssuedQuantity(), Math.max(line.getReturnedQuantity(), returnedTotal));
                    int delta = newReturned - line.getReturnedQuantity();
                    line.setReturnedQuantity(newReturned);
                    equipmentReservationItems.save(line);
                    if (delta > 0) {
                        EquipmentHandoverItem hi = new EquipmentHandoverItem();
                        hi.setHandoverRecordId(savedRecord.getId());
                        hi.setEquipmentId(line.getEquipmentId());
                        hi.setQuantity(delta);
                        hi.setCondition("good");
                        equipmentHandoverItems.save(hi);
                    }
                }
            }
            boolean allReturned = lines.stream().allMatch(l -> l.getIssuedQuantity().equals(l.getReturnedQuantity()));
            res.setStatus(allReturned ? "returned" : "return_required");
            equipmentReservations.save(res);
        }

        return buildOperationsReportResponse(schedule).checkOutReport();
    }

    private ScheduleOperationsReportResponse buildOperationsReportResponse(TourSchedule schedule) {
        long scheduleId = schedule.getId();
        List<ScheduleAttendanceBatch> scheduleBatches = batches.findAllByScheduleIdOrderByIdAsc(scheduleId);
        ScheduleAttendanceBatch checkInBatch = scheduleBatches.stream()
                .filter(b -> "CHECK_IN".equals(b.getRoundType()))
                .findFirst()
                .orElse(null);
        ScheduleAttendanceBatch checkOutBatch = scheduleBatches.stream()
                .filter(b -> "CHECK_OUT".equals(b.getRoundType()))
                .findFirst()
                .orElse(null);

        if (checkInBatch == null && checkOutBatch == null) {
            return new ScheduleOperationsReportResponse(
                    scheduleId,
                    schedule.getTourId(),
                    schedule.getTour() != null ? schedule.getTour().getTitle() : "Chuyến Trekking",
                    schedule.getStartsAt(),
                    schedule.getEndsAt(),
                    null,
                    null,
                    OffsetDateTime.now(clock)
            );
        }

        List<Booking> activeBookings = bookings.findAllByScheduleIdAndStatusInOrderByIdAsc(
                scheduleId, ACTIVE_TRIP_BOOKING_STATUSES);
        Map<Long, Booking> bookingMap = activeBookings.stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        List<Participant> scheduleParticipants = activeBookings.isEmpty()
                ? List.of()
                : participants.findAllByBookingIdInOrderByIdAsc(new ArrayList<>(bookingMap.keySet()));
        Map<Long, Participant> participantMap = scheduleParticipants.stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));

        List<ScheduleParticipantAttendance> attendances =
                participantAttendances.findAllByScheduleIdOrderByIdAsc(scheduleId);
        List<ParticipantEquipmentHandover> handovers =
                participantHandovers.findAllByScheduleIdOrderByIdAsc(scheduleId);

        Set<Long> equipmentIds = handovers.stream()
                .map(ParticipantEquipmentHandover::getEquipmentId)
                .collect(Collectors.toSet());
        Map<Long, String> equipmentNameMap = equipmentIds.isEmpty()
                ? Map.of()
                : equipmentCatalog.findAllById(equipmentIds).stream()
                        .collect(Collectors.toMap(Equipment::getId, Equipment::getName));

        Map<Long, List<ParticipantEquipmentHandover>> handoversByParticipant = handovers.stream()
                .collect(Collectors.groupingBy(
                        ParticipantEquipmentHandover::getParticipantId, LinkedHashMap::new, Collectors.toList()));

        ScheduleOperationsReportResponse.CheckInBatchReportResponse checkInDto = null;
        if (checkInBatch != null) {
            AppAccount guideAcc = accounts.findById(checkInBatch.getSubmittedByAccountId()).orElse(null);
            List<ScheduleOperationsReportResponse.ParticipantCheckInItemResponse> pItems = attendances.stream()
                    .map(att -> {
                        Participant p = participantMap.get(att.getParticipantId());
                        Booking b = bookingMap.get(att.getBookingId());
                        boolean isBookingCustomer = p != null && b != null
                                && p.getAccountId() != null && p.getAccountId().equals(b.getCustomerId());

                        List<ScheduleOperationsReportResponse.EquipmentCheckInItemResponse> eqItems =
                                handoversByParticipant.getOrDefault(att.getParticipantId(), List.of())
                                        .stream()
                                        .map(h -> new ScheduleOperationsReportResponse.EquipmentCheckInItemResponse(
                                                scheduleId,
                                                h.getBookingId(),
                                                h.getParticipantId(),
                                                h.getEquipmentId(),
                                                equipmentNameMap.getOrDefault(h.getEquipmentId(), "Thiết bị #" + h.getEquipmentId()),
                                                h.getAllocatedQuantity(),
                                                h.getCheckInCondition(),
                                                h.getCheckInNote(),
                                                parsePhotos(h.getCheckInPhotosJson()),
                                                h.getHandedOverAt()
                                        ))
                                        .toList();

                        return new ScheduleOperationsReportResponse.ParticipantCheckInItemResponse(
                                scheduleId,
                                att.getBookingId(),
                                b != null ? b.getBookingCode() : "#" + att.getBookingId(),
                                att.getParticipantId(),
                                p != null ? p.getFullName() : "Hành khách #" + att.getParticipantId(),
                                p != null ? p.getPhone() : null,
                                p != null ? p.getEmail() : null,
                                isBookingCustomer,
                                att.getCheckInStatus(),
                                att.getCheckInMarkedAt(),
                                att.getCheckInNote(),
                                eqItems
                        );
                    })
                    .toList();

            checkInDto = new ScheduleOperationsReportResponse.CheckInBatchReportResponse(
                    scheduleId,
                    "CONFIRMED",
                    checkInBatch.getIdempotencyKey(),
                    checkInBatch.getStartedAt(),
                    checkInBatch.getSubmittedAt(),
                    checkInBatch.getSubmittedByGuideId(),
                    guideAcc != null ? guideAcc.getFullName() : "Hướng dẫn viên",
                    guideAcc != null ? guideAcc.getEmail() : null,
                    checkInBatch.getGeneralNote(),
                    null,
                    pItems
            );
        }

        ScheduleOperationsReportResponse.CheckOutBatchReportResponse checkOutDto = null;
        if (checkOutBatch != null) {
            AppAccount guideAcc = accounts.findById(checkOutBatch.getSubmittedByAccountId()).orElse(null);
            List<ScheduleOperationsReportResponse.ParticipantCheckOutItemResponse> pItems = attendances.stream()
                    .map(att -> {
                        Participant p = participantMap.get(att.getParticipantId());
                        Booking b = bookingMap.get(att.getBookingId());
                        boolean isBookingCustomer = p != null && b != null
                                && p.getAccountId() != null && p.getAccountId().equals(b.getCustomerId());

                        List<ScheduleOperationsReportResponse.EquipmentCheckOutItemResponse> eqItems =
                                handoversByParticipant.getOrDefault(att.getParticipantId(), List.of())
                                        .stream()
                                        .map(h -> new ScheduleOperationsReportResponse.EquipmentCheckOutItemResponse(
                                                scheduleId,
                                                h.getBookingId(),
                                                h.getParticipantId(),
                                                h.getEquipmentId(),
                                                equipmentNameMap.getOrDefault(h.getEquipmentId(), "Thiết bị #" + h.getEquipmentId()),
                                                h.getAllocatedQuantity(),
                                                h.getReturnedQuantity() != null ? h.getReturnedQuantity() : 0,
                                                h.getCheckOutCondition() != null ? h.getCheckOutCondition() : "GOOD",
                                                h.getCheckOutNote(),
                                                parsePhotos(h.getCheckOutPhotosJson()),
                                                h.getReturnedAt()
                                        ))
                                        .toList();

                        return new ScheduleOperationsReportResponse.ParticipantCheckOutItemResponse(
                                scheduleId,
                                att.getBookingId(),
                                b != null ? b.getBookingCode() : "#" + att.getBookingId(),
                                att.getParticipantId(),
                                p != null ? p.getFullName() : "Hành khách #" + att.getParticipantId(),
                                p != null ? p.getPhone() : null,
                                p != null ? p.getEmail() : null,
                                isBookingCustomer,
                                att.getCheckInStatus(),
                                att.getCheckOutStatus() != null ? att.getCheckOutStatus() : "COMPLETED",
                                att.getCheckOutMarkedAt(),
                                att.getCheckOutNote(),
                                eqItems
                        );
                    })
                    .toList();

            checkOutDto = new ScheduleOperationsReportResponse.CheckOutBatchReportResponse(
                    scheduleId,
                    "CONFIRMED",
                    checkOutBatch.getIdempotencyKey(),
                    checkOutBatch.getStartedAt(),
                    checkOutBatch.getSubmittedAt(),
                    checkOutBatch.getSubmittedByGuideId(),
                    guideAcc != null ? guideAcc.getFullName() : "Hướng dẫn viên",
                    guideAcc != null ? guideAcc.getEmail() : null,
                    checkOutBatch.getGeneralNote(),
                    null,
                    pItems
            );
        }

        OffsetDateTime updatedAt = checkOutBatch != null
                ? checkOutBatch.getSubmittedAt()
                : checkInBatch != null ? checkInBatch.getSubmittedAt() : OffsetDateTime.now(clock);

        return new ScheduleOperationsReportResponse(
                scheduleId,
                schedule.getTourId(),
                schedule.getTour() != null ? schedule.getTour().getTitle() : "Chuyến Trekking",
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                checkInDto,
                checkOutDto,
                updatedAt
        );
    }

    private void authorizeScheduleRead(long scheduleId) {
        var roles = authorization.currentRoles();
        if (roles.contains(RoleType.ADMIN)) {
            authorization.require(Permission.TOUR_READ);
        } else if (roles.contains(RoleType.GUIDE)) {
            authorization.requireGuideSchedule(scheduleId);
        } else {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Action is not permitted");
        }
    }

    private String serializePhotos(List<ScheduleCheckInBatchRequest.PhotoRecordRequest> photos) {
        if (photos == null || photos.isEmpty()) return "[]";
        try {
            return json.writeValueAsString(photos);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<ScheduleOperationsReportResponse.PhotoItemResponse> parsePhotos(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return List.of();
        try {
            return json.readValue(
                    jsonStr,
                    new TypeReference<List<ScheduleOperationsReportResponse.PhotoItemResponse>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
