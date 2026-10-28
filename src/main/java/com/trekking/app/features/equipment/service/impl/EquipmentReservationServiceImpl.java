package com.trekking.app.features.equipment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.equipment.dto.request.EquipmentHoldRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentHoldResult;
import com.trekking.app.features.equipment.entity.EquipmentInventory;
import com.trekking.app.features.equipment.entity.EquipmentReservation;
import com.trekking.app.features.equipment.entity.EquipmentReservationItem;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.equipment.repository.TourEquipmentPackageLookupRepository;
import com.trekking.app.features.equipment.service.EquipmentReservationService;
import com.trekking.app.features.tour.entity.TourEquipmentItem;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.repository.TourEquipmentItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class EquipmentReservationServiceImpl implements EquipmentReservationService {
    private static final String HELD = "held";
    private static final String CONFIRMED = "confirmed";
    private static final String RELEASED = "released";
    private static final String ISSUED = "issued";
    private static final String RETURN_REQUIRED = "return_required";
    private static final String RETURNED = "returned";

    private final EquipmentInventoryRepository equipment;
    private final TourEquipmentItemRepository packageItems;
    private final TourEquipmentPackageLookupRepository packages;
    private final EquipmentReservationRepository reservations;
    private final EquipmentReservationItemRepository reservationItems;
    private final EntityManager entities;
    private final ObjectMapper json;
    private final Clock clock;

    public EquipmentReservationServiceImpl(EquipmentInventoryRepository equipment, TourEquipmentItemRepository packageItems,
            TourEquipmentPackageLookupRepository packages, EquipmentReservationRepository reservations,
            EquipmentReservationItemRepository reservationItems, EntityManager entities,
            ObjectMapper json, Clock clock) {
        this.equipment = equipment;
        this.packageItems = packageItems;
        this.packages = packages;
        this.reservations = reservations;
        this.reservationItems = reservationItems;
        this.entities = entities;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EquipmentHoldResult hold(EquipmentHoldRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (request.bookingId() <= 0 || request.scheduleId() <= 0 || request.participantCount() <= 0
                || request.mode() == null || (request.packageId() != null && request.packageId() <= 0)) {
            throw conflict("INVALID_EQUIPMENT_HOLD_REQUEST", "Booking, schedule, package and participant values must be positive");
        }
        Map<Long, Integer> selectedItems = request.itemQuantities() == null ? Map.of() : request.itemQuantities();
        if (selectedItems.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null || entry.getValue() <= 0)) {
            throw conflict("INVALID_EQUIPMENT_QUANTITY", "Equipment IDs and selected quantities must be positive");
        }
        if (request.mode() == EquipmentHoldRequest.Mode.NONE) {
            if (request.packageId() != null || request.packageQuantity() != null || !selectedItems.isEmpty()) {
                throw conflict("INVALID_EQUIPMENT_SELECTION", "NONE selection cannot include package or item details");
            }
        } else if (request.mode() == EquipmentHoldRequest.Mode.PACKAGE) {
            if (request.packageId() == null || request.packageId() <= 0
                    || request.packageQuantity() == null || request.packageQuantity() <= 0 || !selectedItems.isEmpty()) {
                throw conflict("INVALID_EQUIPMENT_SELECTION", "PACKAGE selection requires a positive package quantity and no item selections");
            }
        } else if (request.packageId() != null || request.packageQuantity() != null || selectedItems.isEmpty()) {
            throw conflict("INVALID_EQUIPMENT_SELECTION", "ITEMS selection requires item quantities and no package fields");
        }
        if (request.holdExpiresAt() == null || !request.holdExpiresAt().isAfter(now)
                || request.holdExpiresAt().isAfter(now.plusMinutes(15))) {
            throw conflict("INVALID_EQUIPMENT_HOLD_EXPIRY", "Equipment holds must expire within 15 minutes");
        }
        String selectionKey = selectionKey(request, selectedItems);
        TourSchedule schedule = entities.find(TourSchedule.class, request.scheduleId(), LockModeType.PESSIMISTIC_WRITE);
        if (schedule == null) throw notFound();
        Booking booking = entities.find(Booking.class, request.bookingId());
        if (booking == null || !booking.getScheduleId().equals(schedule.getId())) throw notFound();

        var existing = reservations.findByBookingIdForUpdate(request.bookingId());
        if (existing.isPresent()) {
            var current = existing.get();
            if (!current.getSelectionKey().equals(selectionKey)
                    || !current.getParticipantCount().equals(request.participantCount())) {
                throw conflict("EQUIPMENT_HOLD_ALREADY_EXISTS", "Booking already has a different equipment selection");
            }
            if (current.getStatus().equals(RELEASED) || current.getStatus().equals(RETURNED)) {
                throw conflict("EQUIPMENT_HOLD_RELEASED", "Equipment hold for this booking was already released");
            }
            return new EquipmentHoldResult(current.getId(), current.getPackageId() != null,
                    current.getRentalFeeVnd(), current.getEquipmentSnapshot(), current.getStatus());
        }

        if (request.mode() == EquipmentHoldRequest.Mode.NONE) {
            return new EquipmentHoldResult(null, false, 0,
                    serialize(Map.of("mode", "NONE", "participantCount", request.participantCount())), null);
        }

        var equipmentPackage = request.mode() == EquipmentHoldRequest.Mode.PACKAGE
                ? packages.findById(request.packageId()).orElseThrow(EquipmentReservationServiceImpl::notFound)
                : packages.findByTourId(schedule.getTourId()).orElseThrow(EquipmentReservationServiceImpl::notFound);
        if (!equipmentPackage.getTourId().equals(schedule.getTourId())) {
            throw conflict("EQUIPMENT_PACKAGE_NOT_AVAILABLE", "Equipment package is not available for this schedule");
        }
        if (request.mode() == EquipmentHoldRequest.Mode.PACKAGE
                && (equipmentPackage.getRentalPriceVnd() == null || equipmentPackage.getRentalPriceVnd() < 0)) {
            throw conflict("EQUIPMENT_PACKAGE_PRICE_MISSING", "Equipment package rental price is not configured");
        }

        var items = packageItems.findAllByPackageIdOrderByIdAsc(equipmentPackage.getId());
        if (items.isEmpty()) throw conflict("EQUIPMENT_PACKAGE_EMPTY", "Equipment package has no equipment items");
        Map<Long, TourEquipmentItem> itemsById = new LinkedHashMap<>();
        items.forEach(item -> itemsById.put(item.getId(), item));
        List<TourEquipmentItem> selectedLines;
        Map<Long, Integer> quantitiesByItemId = new LinkedHashMap<>();
        if (request.mode() == EquipmentHoldRequest.Mode.PACKAGE) {
            selectedLines = items;
            for (var item : selectedLines) {
                quantitiesByItemId.put(item.getId(), multiplyQuantity(item.getQuantityInPackage(), request.packageQuantity()));
            }
        } else {
            if (!itemsById.keySet().containsAll(selectedItems.keySet())) {
                throw conflict("EQUIPMENT_NOT_IN_PACKAGE", "Selected item is not included in this package");
            }
            selectedLines = selectedItems.keySet().stream().sorted().map(itemsById::get).toList();
            quantitiesByItemId.putAll(selectedItems);
        }

        List<Long> equipmentIds = selectedLines.stream().map(item -> item.getEquipmentId()).distinct().sorted().toList();
        var lockedEquipment = equipment.findAllForUpdate(equipmentIds);
        if (lockedEquipment.size() != equipmentIds.size()) throw notFound();
        Map<Long, EquipmentInventory> byId = new LinkedHashMap<>();
        lockedEquipment.forEach(value -> byId.put(value.getEquipmentId(), value));

        var quantities = new LinkedHashMap<Long, Integer>();
        var snapshotItems = new ArrayList<Map<String, Object>>();
        long fee = 0L;
        for (var packageItem : selectedLines) {
            var inventoryItem = byId.get(packageItem.getEquipmentId());
            if (!Boolean.TRUE.equals(inventoryItem.getActive())) throw conflict("EQUIPMENT_INACTIVE", "An item in this package is inactive");
            int selected = quantitiesByItemId.get(packageItem.getId());
            long committed = reservations.committedQuantity(inventoryItem.getEquipmentId(), schedule.getStartsAt(), schedule.getEndsAt(), now);
            int available = inventoryItem.getTotalQuantity() - inventoryItem.getMaintenanceQuantity() - Math.toIntExact(committed);
            if (selected > available) throw conflict("EQUIPMENT_UNAVAILABLE", "Not enough equipment is available for this schedule");
            quantities.merge(inventoryItem.getEquipmentId(), selected, Math::addExact);
            long unitPrice = request.mode() == EquipmentHoldRequest.Mode.PACKAGE
                    ? 0L : packageItem.getIndividualRentalPriceVnd() == null ? -1L : packageItem.getIndividualRentalPriceVnd();
            if (request.mode() == EquipmentHoldRequest.Mode.ITEMS && unitPrice < 0) {
                throw conflict("EQUIPMENT_NOT_RENTABLE", "A selected item does not have an individual rental price");
            }
            long lineFee = request.mode() == EquipmentHoldRequest.Mode.ITEMS
                    ? multiplyMoney(unitPrice, selected) : 0L;
            fee = addMoney(fee, lineFee);
            var lineSnapshot = new LinkedHashMap<String, Object>();
            lineSnapshot.put("itemId", packageItem.getId());
            lineSnapshot.put("equipmentId", inventoryItem.getEquipmentId());
            lineSnapshot.put("name", inventoryItem.getEquipment().getName());
            lineSnapshot.put("quantityInPackage", packageItem.getQuantityInPackage());
            lineSnapshot.put("selectedQuantity", selected);
            if (request.mode() == EquipmentHoldRequest.Mode.ITEMS) lineSnapshot.put("unitPriceVnd", unitPrice);
            snapshotItems.add(lineSnapshot);
        }
        if (request.mode() == EquipmentHoldRequest.Mode.PACKAGE) {
            fee = multiplyMoney(equipmentPackage.getRentalPriceVnd(), request.packageQuantity());
        }
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("mode", request.mode().name());
        snapshot.put("packageId", equipmentPackage.getId());
        snapshot.put("title", equipmentPackage.getTitle());
        snapshot.put("participantCount", request.participantCount());
        snapshot.put("packageQuantity", request.packageQuantity());
        if (request.mode() == EquipmentHoldRequest.Mode.PACKAGE) {
            snapshot.put("unitPriceVnd", equipmentPackage.getRentalPriceVnd());
        }
        snapshot.put("rentalFeeVnd", fee);
        snapshot.put("items", snapshotItems);

        var reservation = new EquipmentReservation();
        reservation.setBookingId(request.bookingId());
        reservation.setScheduleId(schedule.getId());
        reservation.setPackageId(equipmentPackage.getId());
        reservation.setSelectionKey(selectionKey);
        reservation.setStatus(HELD);
        reservation.setHoldExpiresAt(request.holdExpiresAt());
        reservation.setParticipantCount(request.participantCount());
        reservation.setRentalFeeVnd(fee);
        reservation.setEquipmentSnapshot(serialize(snapshot));
        reservation = reservations.save(reservation);

        for (var selection : quantities.entrySet()) {
            var line = new EquipmentReservationItem();
            line.setReservationId(reservation.getId());
            line.setEquipmentId(selection.getKey());
            line.setQuantity(selection.getValue());
            reservationItems.save(line);
        }
        return new EquipmentHoldResult(reservation.getId(), true, fee, reservation.getEquipmentSnapshot(), HELD);
    }

    private String selectionKey(EquipmentHoldRequest request, Map<Long, Integer> selectedItems) {
        var key = new LinkedHashMap<String, Object>();
        key.put("mode", request.mode().name());
        key.put("packageId", request.packageId());
        key.put("packageQuantity", request.packageQuantity());
        key.put("itemQuantities", new TreeMap<>(selectedItems));
        return serialize(key);
    }

    private static int multiplyQuantity(int quantityPerPackage, int packageCount) {
        try {
            return Math.multiplyExact(quantityPerPackage, packageCount);
        } catch (ArithmeticException overflow) {
            throw conflict("EQUIPMENT_QUANTITY_OUT_OF_RANGE", "Selected equipment quantity is outside the supported range");
        }
    }

    private static long multiplyMoney(long unitPrice, int quantity) {
        try {
            long amount = Math.multiplyExact(unitPrice, (long) quantity);
            if (amount > 1_000_000_000_000L) throw conflict("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
            return amount;
        } catch (ArithmeticException overflow) {
            throw conflict("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
        }
    }

    private static long addMoney(long left, long right) {
        try {
            long amount = Math.addExact(left, right);
            if (amount > 1_000_000_000_000L) throw conflict("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
            return amount;
        } catch (ArithmeticException overflow) {
            throw conflict("PRICE_OUT_OF_RANGE", "The rental total is outside the supported range");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(long bookingId) {
        var reservation = reservations.findByBookingIdForUpdate(bookingId).orElse(null);
        if (reservation == null) return;
        if (reservation.getStatus().equals(CONFIRMED)) return;
        if (!reservation.getStatus().equals(HELD)) return;
        if (!reservation.getHoldExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            reservation.setStatus(RELEASED);
            reservations.save(reservation);
            return;
        }
        reservation.setStatus(CONFIRMED);
        reservations.save(reservation);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long bookingId) {
        var reservation = reservations.findByBookingIdForUpdate(bookingId).orElse(null);
        if (reservation == null || reservation.getStatus().equals(RELEASED)
                || reservation.getStatus().equals(RETURNED) || reservation.getStatus().equals(RETURN_REQUIRED)) return;
        boolean issuedOutstanding = reservationItems.findAllByReservationIdOrderByIdAsc(reservation.getId()).stream()
                .anyMatch(item -> item.getIssuedQuantity() > item.getReturnedQuantity());
        reservation.setStatus(issuedOutstanding ? RETURN_REQUIRED : RELEASED);
        reservations.save(reservation);
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public int releaseExpiredHolds() {
        var expired = reservations.findExpiredHolds(OffsetDateTime.now(clock), PageRequest.of(0, 500));
        for (var candidate : expired) {
            var reservation = reservations.findByBookingIdForUpdate(candidate.getBookingId()).orElse(null);
            if (reservation != null && reservation.getStatus().equals(HELD)
                    && !reservation.getHoldExpiresAt().isAfter(OffsetDateTime.now(clock))) {
                reservation.setStatus(RELEASED);
                reservations.save(reservation);
            }
        }
        return expired.size();
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Could not serialize equipment snapshot", ex); }
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
