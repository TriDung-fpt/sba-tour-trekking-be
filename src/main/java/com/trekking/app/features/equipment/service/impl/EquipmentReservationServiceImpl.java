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
import com.trekking.app.features.tour.entity.TourEquipmentPackage;
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
        if (request.bookingId() <= 0 || request.scheduleId() <= 0 || request.participantCount() <= 0) {
            throw conflict("INVALID_EQUIPMENT_HOLD_REQUEST", "Booking, schedule and participant values must be positive");
        }
        Map<Long, Integer> selectedPackages = request.packageQuantities() == null ? Map.of() : request.packageQuantities();
        Map<Long, Integer> selectedItems = request.itemQuantities() == null ? Map.of() : request.itemQuantities();
        if (invalidQuantities(selectedPackages) || invalidQuantities(selectedItems)) {
            throw conflict("INVALID_EQUIPMENT_QUANTITY", "Equipment IDs and selected quantities must be positive");
        }
        if (request.holdExpiresAt() == null || !request.holdExpiresAt().isAfter(now)
                || request.holdExpiresAt().isAfter(now.plusMinutes(15))) {
            throw conflict("INVALID_EQUIPMENT_HOLD_EXPIRY", "Equipment holds must expire within 15 minutes");
        }
        String selectionKey = selectionKey(request, selectedPackages, selectedItems);
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
            return new EquipmentHoldResult(current.getId(), true,
                    current.getRentalFeeVnd(), current.getEquipmentSnapshot(), current.getStatus());
        }

        if (selectedPackages.isEmpty() && selectedItems.isEmpty()) {
            return new EquipmentHoldResult(null, false, 0,
                    serialize(Map.of("mode", "SELF_PROVIDED", "participantCount", request.participantCount())), null);
        }

        var packagesById = new LinkedHashMap<Long, TourEquipmentPackage>();
        var selectedLines = new LinkedHashMap<Long, TourEquipmentItem>();
        var quantitiesByItemId = new LinkedHashMap<Long, Integer>();
        var packageSnapshots = new ArrayList<Map<String, Object>>();
        long fee = 0L;
        for (var selection : new TreeMap<>(selectedPackages).entrySet()) {
            TourEquipmentPackage selectedPackage = packages.findById(selection.getKey()).orElseThrow(EquipmentReservationServiceImpl::notFound);
            if (!selectedPackage.getTourId().equals(schedule.getTourId()) || !Boolean.TRUE.equals(selectedPackage.getActive())
                    || Boolean.TRUE.equals(selectedPackage.getNeedsReview())) {
                throw conflict("EQUIPMENT_PACKAGE_NOT_AVAILABLE", "Selected equipment package is not available for this schedule");
            }
            if (selectedPackage.getRentalPriceVnd() == null || selectedPackage.getRentalPriceVnd() < 0) {
                throw conflict("EQUIPMENT_PACKAGE_PRICE_MISSING", "Equipment package rental price is not configured");
            }
            List<TourEquipmentItem> components = packageItems.findAllByPackageIdOrderByIdAsc(selectedPackage.getId());
            if (components.isEmpty()) throw conflict("EQUIPMENT_PACKAGE_EMPTY", "Selected equipment package has no equipment items");
            packagesById.put(selectedPackage.getId(), selectedPackage);
            var componentSnapshots = new ArrayList<Map<String, Object>>();
            for (var item : components) {
                selectedLines.put(item.getId(), item);
                quantitiesByItemId.put(item.getId(), multiplyQuantity(item.getQuantityInPackage(), selection.getValue()));
                componentSnapshots.add(Map.of("itemId", item.getId(), "equipmentId", item.getEquipmentId(),
                        "name", item.getEquipment().getName(), "quantityInPackage", item.getQuantityInPackage()));
            }
            fee = addMoney(fee, multiplyMoney(selectedPackage.getRentalPriceVnd(), selection.getValue()));
            packageSnapshots.add(Map.of("packageId", selectedPackage.getId(), "title", selectedPackage.getTitle(),
                    "quantity", selection.getValue(), "unitPriceVnd", selectedPackage.getRentalPriceVnd(),
                    "components", componentSnapshots));
        }
        if (!selectedItems.isEmpty()) {
            for (var item : packageItems.findAllByTourIdAndPackageIdIsNullOrderByIdAsc(schedule.getTourId())) {
                selectedLines.put(item.getId(), item);
            }
            if (!selectedLines.keySet().containsAll(selectedItems.keySet())) {
                throw conflict("EQUIPMENT_NOT_RENTABLE", "A selected item is not available for individual rental on this tour");
            }
            for (var selection : new TreeMap<>(selectedItems).entrySet()) {
                TourEquipmentItem item = selectedLines.get(selection.getKey());
                if (item.getPackageId() != null || item.getIndividualRentalPriceVnd() == null || item.getIndividualRentalPriceVnd() < 0) {
                    throw conflict("EQUIPMENT_NOT_RENTABLE", "A selected item is not available for individual rental on this tour");
                }
                quantitiesByItemId.put(item.getId(), selection.getValue());
            }
        }
        selectedLines.keySet().retainAll(quantitiesByItemId.keySet());

        List<Long> equipmentIds = selectedLines.values().stream().map(item -> item.getEquipmentId()).distinct().sorted().toList();
        var lockedEquipment = equipment.findAllForUpdate(equipmentIds);
        if (lockedEquipment.size() != equipmentIds.size()) throw notFound();
        Map<Long, EquipmentInventory> byId = new LinkedHashMap<>();
        lockedEquipment.forEach(value -> byId.put(value.getEquipmentId(), value));

        var snapshotItems = new ArrayList<Map<String, Object>>();
        var quantities = new LinkedHashMap<Long, Integer>();
        for (var packageItem : selectedLines.values()) {
            var inventoryItem = byId.get(packageItem.getEquipmentId());
            if (!Boolean.TRUE.equals(inventoryItem.getActive())) throw conflict("EQUIPMENT_INACTIVE", "A selected equipment item is inactive");
            int selected = quantitiesByItemId.get(packageItem.getId());
            try {
                quantities.merge(inventoryItem.getEquipmentId(), selected, Math::addExact);
            } catch (ArithmeticException overflow) {
                throw conflict("EQUIPMENT_QUANTITY_OUT_OF_RANGE", "Combined equipment quantity is outside the supported range");
            }
            Long unitPrice = packageItem.getPackageId() == null ? packageItem.getIndividualRentalPriceVnd() : null;
            if (unitPrice != null) fee = addMoney(fee, multiplyMoney(unitPrice, selected));
            var lineSnapshot = new LinkedHashMap<String, Object>();
            lineSnapshot.put("itemId", packageItem.getId());
            lineSnapshot.put("equipmentId", inventoryItem.getEquipmentId());
            lineSnapshot.put("name", inventoryItem.getEquipment().getName());
            lineSnapshot.put("quantityInPackage", packageItem.getQuantityInPackage());
            lineSnapshot.put("selectedQuantity", selected);
            if (unitPrice != null) lineSnapshot.put("unitPriceVnd", unitPrice);
            snapshotItems.add(lineSnapshot);
        }
        for (var selection : quantities.entrySet()) {
            var inventoryItem = byId.get(selection.getKey());
            long committed = reservations.committedQuantity(selection.getKey(), schedule.getStartsAt(), schedule.getEndsAt(), now);
            long available = (long) inventoryItem.getTotalQuantity() - inventoryItem.getMaintenanceQuantity() - committed;
            if (selection.getValue() > available) throw conflict("EQUIPMENT_UNAVAILABLE", inventoryItem.getEquipment().getName() + " does not have enough stock for the selected schedule");
        }
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("mode", "SELECTED");
        snapshot.put("packages", packageSnapshots);
        snapshot.put("participantCount", request.participantCount());
        snapshot.put("rentalFeeVnd", fee);
        snapshot.put("items", snapshotItems);

        var reservation = new EquipmentReservation();
        reservation.setBookingId(request.bookingId());
        reservation.setScheduleId(schedule.getId());
        reservation.setPackageId(packagesById.size() == 1 && selectedItems.isEmpty() ? packagesById.keySet().iterator().next() : null);
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

    private String selectionKey(EquipmentHoldRequest request, Map<Long, Integer> selectedPackages, Map<Long, Integer> selectedItems) {
        var key = new LinkedHashMap<String, Object>();
        key.put("packageQuantities", new TreeMap<>(selectedPackages));
        key.put("itemQuantities", new TreeMap<>(selectedItems));
        return serialize(key);
    }

    private static boolean invalidQuantities(Map<Long, Integer> values) {
        return values.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey() <= 0
                || entry.getValue() == null || entry.getValue() <= 0);
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
