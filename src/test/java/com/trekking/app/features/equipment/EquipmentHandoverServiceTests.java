package com.trekking.app.features.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.equipment.dto.request.EquipmentHandoverRequest;
import com.trekking.app.features.equipment.entity.EquipmentHandoverRecord;
import com.trekking.app.features.equipment.entity.EquipmentReservation;
import com.trekking.app.features.equipment.entity.EquipmentReservationItem;
import com.trekking.app.features.equipment.repository.EquipmentHandoverItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentHandoverRepository;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationItemRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.equipment.service.impl.EquipmentHandoverServiceImpl;
import com.trekking.app.features.tour.entity.Equipment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class EquipmentHandoverServiceTests {
    private final EquipmentReservationRepository reservations = mock(EquipmentReservationRepository.class);
    private final EquipmentReservationItemRepository lines = mock(EquipmentReservationItemRepository.class);
    private final EquipmentHandoverRepository records = mock(EquipmentHandoverRepository.class);
    private final EquipmentHandoverItemRepository items = mock(EquipmentHandoverItemRepository.class);
    private final EquipmentInventoryRepository inventory = mock(EquipmentInventoryRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final EquipmentHandoverServiceImpl service = new EquipmentHandoverServiceImpl(
            reservations, lines, records, items, inventory, authorization, clock);

    @Test
    void assignedGuideRecordsEquipmentIssuedToBooking() {
        var reservation = reservation();
        reservation.setStatus("confirmed");
        var line = line();
        line.setIssuedQuantity(0);
        when(reservations.findByBookingIdForUpdate(10)).thenReturn(Optional.of(reservation));
        when(lines.findAllByReservationIdOrderByIdAsc(1L)).thenReturn(List.of(line));
        when(authorization.currentRoles()).thenReturn(Set.of(RoleType.GUIDE));
        when(authorization.currentAccountId()).thenReturn(7L);
        when(records.save(any(EquipmentHandoverRecord.class))).thenAnswer(invocation -> {
            EquipmentHandoverRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 4L);
            return record;
        });

        var result = service.issue(10, request(2));

        assertThat(result.status()).isEqualTo("issued");
        assertThat(result.items().getFirst().issuedQuantity()).isEqualTo(2);
        verify(authorization).requireGuideSchedule(20L);
        var captured = ArgumentCaptor.forClass(EquipmentHandoverRecord.class);
        verify(records).save(captured.capture());
        assertThat(captured.getValue().getAction()).isEqualTo("issued");
        assertThat(captured.getValue().getActorId()).isEqualTo(7L);
    }

    @Test
    void assignedGuideRecordsPartialAndFinalReturns() {
        var reservation = reservation();
        var line = line();
        when(reservations.findByBookingIdForUpdate(10)).thenReturn(Optional.of(reservation));
        when(lines.findAllByReservationIdOrderByIdAsc(1L)).thenReturn(List.of(line));
        when(authorization.currentRoles()).thenReturn(Set.of(RoleType.GUIDE));
        when(authorization.currentAccountId()).thenReturn(7L);
        when(records.save(any(EquipmentHandoverRecord.class))).thenAnswer(invocation -> {
            EquipmentHandoverRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 4L);
            return record;
        });

        var first = service.recordReturn(10, request(1));
        assertThat(first.status()).isEqualTo("return_required");
        assertThat(first.items().getFirst().returnedQuantity()).isEqualTo(1);
        var last = service.recordReturn(10, request(1));
        assertThat(last.status()).isEqualTo("returned");
        assertThat(last.items().getFirst().returnedQuantity()).isEqualTo(2);
        verify(authorization, times(2)).requireGuideSchedule(20L);
        var captured = ArgumentCaptor.forClass(EquipmentHandoverRecord.class);
        verify(records, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(record -> {
            assertThat(record.getAction()).isEqualTo("returned");
            assertThat(record.getActorId()).isEqualTo(7L);
            assertThat(record.getOccurredAt().toInstant()).isEqualTo(clock.instant());
        });
    }

    @Test
    void guideWithoutAssignmentCannotReturnEquipment() {
        when(reservations.findByBookingIdForUpdate(10)).thenReturn(Optional.of(reservation()));
        when(authorization.currentRoles()).thenReturn(Set.of(RoleType.GUIDE));
        doThrow(new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Not found"))
                .when(authorization).requireGuideSchedule(20L);

        assertThatThrownBy(() -> service.recordReturn(10, request(1))).isInstanceOf(AuthException.class);
        verifyNoInteractions(records, lines, items);
    }

    private static EquipmentReservation reservation() {
        var reservation = new EquipmentReservation();
        ReflectionTestUtils.setField(reservation, "id", 1L);
        reservation.setBookingId(10L);
        reservation.setScheduleId(20L);
        reservation.setStatus("issued");
        return reservation;
    }

    private static EquipmentReservationItem line() {
        var equipment = new Equipment();
        equipment.setName("Tent");
        var line = new EquipmentReservationItem();
        line.setEquipmentId(30L);
        line.setQuantity(2);
        line.setIssuedQuantity(2);
        ReflectionTestUtils.setField(line, "equipment", equipment);
        return line;
    }

    private static EquipmentHandoverRequest request(int quantity) {
        return new EquipmentHandoverRequest("Return", List.of(new EquipmentHandoverRequest.Item(
                30, quantity, EquipmentHandoverRequest.Condition.GOOD)));
    }
}
