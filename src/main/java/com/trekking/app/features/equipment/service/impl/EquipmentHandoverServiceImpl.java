package com.trekking.app.features.equipment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.equipment.dto.request.EquipmentHandoverRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentReservationResponse;
import com.trekking.app.features.equipment.entity.EquipmentHandoverItem;
import com.trekking.app.features.equipment.entity.EquipmentHandoverRecord;
import com.trekking.app.features.equipment.entity.EquipmentReservation;
import com.trekking.app.features.equipment.repository.EquipmentHandoverItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentHandoverRepository;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.equipment.service.EquipmentHandoverService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EquipmentHandoverServiceImpl implements EquipmentHandoverService {
    private final EquipmentReservationRepository reservations;
    private final EquipmentReservationItemRepository reservationItems;
    private final EquipmentHandoverRepository handovers;
    private final EquipmentHandoverItemRepository handoverItems;
    private final EquipmentInventoryRepository equipment;
    private final AuthorizationService authorization;
    private final Clock clock;

    public EquipmentHandoverServiceImpl(EquipmentReservationRepository reservations,
            EquipmentReservationItemRepository reservationItems, EquipmentHandoverRepository handovers,
            EquipmentHandoverItemRepository handoverItems, EquipmentInventoryRepository equipment,
            AuthorizationService authorization, Clock clock) {
        this.reservations = reservations;
        this.reservationItems = reservationItems;
        this.handovers = handovers;
        this.handoverItems = handoverItems;
        this.equipment = equipment;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EquipmentReservationResponse issue(long bookingId, EquipmentHandoverRequest request) {
        var reservation = reservation(bookingId);
        authorize(reservation.getScheduleId());
        if (!reservation.getStatus().equals("confirmed")) {
            throw conflict("EQUIPMENT_ISSUE_NOT_ALLOWED", "Only confirmed equipment can be issued");
        }
        var lines = reservationItems.findAllByReservationIdOrderByIdAsc(reservation.getId());
        var record = newRecord(reservation, "issued", request.note());
        var savedRecord = handovers.save(record);
        for (var selection : request.items()) {
            var line = lines.stream().filter(value -> value.getEquipmentId() == selection.equipmentId())
                    .findFirst().orElseThrow(EquipmentHandoverServiceImpl::notFound);
            int remaining = line.getQuantity() - line.getIssuedQuantity();
            if (selection.quantity() > remaining) throw conflict("EQUIPMENT_ISSUE_EXCEEDS_RESERVATION", "Issued quantity exceeds reservation");
            line.setIssuedQuantity(line.getIssuedQuantity() + selection.quantity());
            reservationItems.save(line);
            saveHandoverItem(savedRecord.getId(), selection.equipmentId(), selection.quantity(), "good");
        }
        boolean fullyIssued = lines.stream().allMatch(line -> line.getIssuedQuantity() >= line.getQuantity());
        if (fullyIssued) reservation.setStatus("issued");
        reservations.save(reservation);
        return response(reservation);
    }

    @Override
    @Transactional
    public EquipmentReservationResponse recordReturn(long bookingId, EquipmentHandoverRequest request) {
        var reservation = reservation(bookingId);
        authorize(reservation.getScheduleId());
        if (!List.of("issued", "return_required", "confirmed").contains(reservation.getStatus())) {
            throw conflict("EQUIPMENT_RETURN_NOT_ALLOWED", "Equipment is not in a returnable state");
        }
        var lines = reservationItems.findAllByReservationIdOrderByIdAsc(reservation.getId());
        var record = handovers.save(newRecord(reservation, "returned", request.note()));
        for (var selection : request.items()) {
            if (selection.condition() == null) throw conflict("EQUIPMENT_CONDITION_REQUIRED", "Return condition is required");
            var line = lines.stream().filter(value -> value.getEquipmentId() == selection.equipmentId())
                    .findFirst().orElseThrow(EquipmentHandoverServiceImpl::notFound);
            int outstanding = line.getIssuedQuantity() - line.getReturnedQuantity();
            if (selection.quantity() > outstanding) throw conflict("EQUIPMENT_RETURN_EXCEEDS_ISSUED", "Returned quantity exceeds issued quantity");
            line.setReturnedQuantity(line.getReturnedQuantity() + selection.quantity());
            reservationItems.save(line);
            String condition = selection.condition() == EquipmentHandoverRequest.Condition.GOOD ? "good" : "needs_inspection";
            saveHandoverItem(record.getId(), selection.equipmentId(), selection.quantity(), condition);
            if (selection.condition() == EquipmentHandoverRequest.Condition.NEEDS_INSPECTION) {
                var item = equipment.findById(selection.equipmentId()).orElseThrow(EquipmentHandoverServiceImpl::notFound);
                item.setMaintenanceQuantity(Math.addExact(item.getMaintenanceQuantity(), selection.quantity()));
                equipment.save(item);
            }
        }
        boolean allReturned = lines.stream().allMatch(line -> line.getIssuedQuantity().equals(line.getReturnedQuantity()));
        if (allReturned) reservation.setStatus("returned");
        else reservation.setStatus("return_required");
        reservations.save(reservation);
        return response(reservation);
    }

    @Override
    public EquipmentReservationResponse read(long bookingId) {
        var reservation = reservations.findByBookingId(bookingId).orElseThrow(EquipmentHandoverServiceImpl::notFound);
        authorize(reservation.getScheduleId());
        return response(reservation);
    }

    private EquipmentReservation reservation(long bookingId) {
        return reservations.findByBookingIdForUpdate(bookingId).orElseThrow(EquipmentHandoverServiceImpl::notFound);
    }

    private void authorize(long scheduleId) {
        var roles = authorization.currentRoles();
        if (roles.contains(RoleType.ADMIN)) {
            authorization.require(Permission.TOUR_MANAGE);
        } else if (roles.contains(RoleType.GUIDE)) {
            authorization.requireGuideSchedule(scheduleId);
        } else {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Action is not permitted");
        }
    }

    private EquipmentHandoverRecord newRecord(EquipmentReservation reservation, String action, String note) {
        var record = new EquipmentHandoverRecord();
        record.setReservationId(reservation.getId());
        record.setAction(action);
        record.setActorId(authorization.currentAccountId());
        record.setNote(note == null || note.isBlank() ? null : note.trim());
        record.setOccurredAt(OffsetDateTime.now(clock));
        return record;
    }

    private void saveHandoverItem(long recordId, long equipmentId, int quantity, String condition) {
        var item = new EquipmentHandoverItem();
        item.setHandoverRecordId(recordId);
        item.setEquipmentId(equipmentId);
        item.setQuantity(quantity);
        item.setCondition(condition);
        handoverItems.save(item);
    }

    private EquipmentReservationResponse response(EquipmentReservation reservation) {
        var items = reservationItems.findAllByReservationIdOrderByIdAsc(reservation.getId()).stream()
                .map(item -> new EquipmentReservationResponse.Item(item.getEquipmentId(), item.getEquipment().getName(),
                        item.getQuantity(), item.getIssuedQuantity(), item.getReturnedQuantity())).toList();
        return new EquipmentReservationResponse(reservation.getId(), reservation.getBookingId(), reservation.getScheduleId(),
                reservation.getPackageId(), reservation.getStatus(), reservation.getHoldExpiresAt(), items);
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
