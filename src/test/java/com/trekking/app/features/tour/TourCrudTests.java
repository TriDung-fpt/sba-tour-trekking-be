package com.trekking.app.features.tour;

import com.trekking.app.core.exception.GlobalExceptionHandler;
import com.trekking.app.features.policy.controller.PolicyController;
import com.trekking.app.features.policy.service.PolicyService;
import com.trekking.app.features.tour.controller.TourController;
import com.trekking.app.features.tour.controller.TourScheduleController;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.exception.TourGlobalExceptionHandler;
import com.trekking.app.features.tour.mapper.TourMapper;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.impl.TourServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TourCrudTests {
    private static final String TOURS = "/api/v1/tours";
    private static final String SCHEDULES = TOURS + "/1/schedules";
    private static final String SCHEDULE = """
            {
              "startsAt": "2027-01-10T07:00:00+07:00",
              "endsAt": "2027-01-12T17:00:00+07:00",
              "capacity": 10,
              "basePriceVnd": 1500000,
              "equipmentFeeVnd": 200000,
              "depositPercent": 30.00,
              "balanceDueAt": "2027-01-09T17:00:00+07:00",
              "meetingPoint": "Da Lat",
              "contactPhone": "0900000000"
            }
            """;
    private TourRepository tours;
    private TourScheduleRepository schedules;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        tours = mock(TourRepository.class);
        schedules = mock(TourScheduleRepository.class);
        var service = new TourServiceImpl(tours, schedules, Mappers.getMapper(TourMapper.class));
        mvc = MockMvcBuilders.standaloneSetup(new TourController(service), new TourScheduleController(service))
                .setControllerAdvice(new TourGlobalExceptionHandler(), new GlobalExceptionHandler()).build();
        when(tours.saveAndFlush(any(Tour.class))).thenAnswer(invocation -> {
            Tour tour = invocation.getArgument(0);
            ReflectionTestUtils.setField(tour, "id", 1L);
            return tour;
        });
        when(schedules.saveAndFlush(any(TourSchedule.class))).thenAnswer(invocation -> {
            TourSchedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 5L);
            return schedule;
        });
    }

    @Test
    void createsDraftTourAndUpdatesOnlyEditableFields() throws Exception {
        mvc.perform(post(TOURS).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"  Ta Nang  ","difficulty":"MODERATE","durationDays":2}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Ta Nang"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        var tour = new Tour();
        when(tours.findLockedById(1)).thenReturn(Optional.of(tour));
        mvc.perform(put(TOURS + "/1").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Updated tour","durationDays":3}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated tour"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createsScheduleUsingParentIdAndServerOwnedCounters() throws Exception {
        when(tours.findLockedById(1)).thenReturn(Optional.of(new Tour()));
        mvc.perform(post(SCHEDULES).contentType(MediaType.APPLICATION_JSON).content(SCHEDULE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tourId").value(1))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.heldSeats").value(0))
                .andExpect(jsonPath("$.confirmedSeats").value(0))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(post(TOURS + "/999/schedules").contentType(MediaType.APPLICATION_JSON).content(SCHEDULE))
                .andExpect(status().isNotFound());
        verify(schedules, times(1)).saveAndFlush(any());
    }

    @Test
    void scheduleCannotBeReadUpdatedOrDeletedThroughAnotherTour() throws Exception {
        mvc.perform(get(SCHEDULES + "/5")).andExpect(status().isNotFound());
        mvc.perform(put(SCHEDULES + "/5").contentType(MediaType.APPLICATION_JSON).content(SCHEDULE))
                .andExpect(status().isNotFound());
        mvc.perform(delete(SCHEDULES + "/5")).andExpect(status().isNotFound());
        verify(schedules, never()).saveAndFlush(any());
        verify(schedules, never()).delete(any(TourSchedule.class));
    }

    @Test
    void validatesTourAndScheduleInputsBeforeSaving() throws Exception {
        mvc.perform(post(TOURS).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"   ","durationDays":0}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for (String invalid : new String[] {
                SCHEDULE.replace("\"capacity\": 10", "\"capacity\": 0"),
                SCHEDULE.replace("1500000", "-1"),
                SCHEDULE.replace("30.00", "100.01"),
                SCHEDULE.replace("30.00", "0"),
                SCHEDULE.replace("30.00", "30.001")}) {
            mvc.perform(post(SCHEDULES).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verify(tours, never()).saveAndFlush(any());
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    void validatesTimeOrderAcrossFields() throws Exception {
        when(tours.findLockedById(1)).thenReturn(Optional.of(new Tour()));
        mvc.perform(post(SCHEDULES).contentType(MediaType.APPLICATION_JSON)
                        .content(SCHEDULE.replace("2027-01-12T17:00:00+07:00", "2027-01-10T07:00:00+07:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_DATES"));
        mvc.perform(post(SCHEDULES).contentType(MediaType.APPLICATION_JSON)
                        .content(SCHEDULE.replace("2027-01-09T17:00:00+07:00", "2027-01-11T17:00:00+07:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BALANCE_DUE_AT"));
        verify(schedules, never()).saveAndFlush(any());
    }

    @Test
    void updatesUnusedScheduleAndPreservesParentAndCounters() throws Exception {
        var schedule = new TourSchedule();
        schedule.setTourId(1L);
        when(schedules.findLockedByIdAndTourId(5, 1)).thenReturn(Optional.of(schedule));
        mvc.perform(put(SCHEDULES + "/5").contentType(MediaType.APPLICATION_JSON).content(SCHEDULE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tourId").value(1))
                .andExpect(jsonPath("$.heldSeats").value(0));
        assertThat(schedule.getBasePriceVnd()).isEqualTo(1500000L);
    }

    @Test
    void blocksChangesToReservedOrStartedSchedules() throws Exception {
        var schedule = new TourSchedule();
        when(schedules.findLockedByIdAndTourId(5, 1)).thenReturn(Optional.of(schedule));
        schedule.setHeldSeats(1);
        rejectScheduleChanges();
        schedule.setHeldSeats(0);
        schedule.setConfirmedSeats(1);
        rejectScheduleChanges();
        schedule.setConfirmedSeats(0);
        schedule.setStatus(ScheduleStatus.ONGOING);
        rejectScheduleChanges();
        verify(schedules, never()).saveAndFlush(any());
        verify(schedules, never()).delete(any(TourSchedule.class));
    }

    @Test
    void blocksTourDeletionUntilItsSchedulesAreRemoved() throws Exception {
        var tour = new Tour();
        when(tours.findLockedById(1)).thenReturn(Optional.of(tour));
        when(schedules.existsByTourId(1)).thenReturn(true);
        mvc.perform(delete(TOURS + "/1")).andExpect(status().isConflict());
        verify(tours, never()).delete(any(Tour.class));
        when(schedules.existsByTourId(1)).thenReturn(false);
        mvc.perform(delete(TOURS + "/1")).andExpect(status().isNoContent());
        verify(tours).delete(tour);
    }

    @Test
    void referencedScheduleDeletionReturnsConflictInsteadOfCascading() throws Exception {
        var schedule = new TourSchedule();
        when(schedules.findLockedByIdAndTourId(5, 1)).thenReturn(Optional.of(schedule));
        doThrow(new DataIntegrityViolationException("referenced schedule")).when(schedules).flush();
        mvc.perform(delete(SCHEDULES + "/5")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_IN_USE"));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mvc.perform(get(TOURS).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(TOURS).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void translatesTourDatabaseErrorsWithoutExposingDatabaseDetails() throws Exception {
        when(tours.findById(1L)).thenThrow(new DataAccessResourceFailureException("private connection details"));
        mvc.perform(get(TOURS + "/1")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TOUR_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Tour service temporarily unavailable"));

        doThrow(new DataIntegrityViolationException("private constraint details")).when(tours).saveAndFlush(any(Tour.class));
        mvc.perform(post(TOURS).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Tour\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOUR_DATA_CONFLICT"));
    }

    @Test
    void malformedTourRequestsStillUseSharedRequestErrorHandling() throws Exception {
        mvc.perform(post(TOURS).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(TOURS + "/invalid-id"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void tourAdviceDoesNotHandleOtherModuleControllers() throws Exception {
        var policies = mock(PolicyService.class);
        when(policies.read(1L)).thenThrow(new DataAccessResourceFailureException("private connection details"));
        var otherMvc = MockMvcBuilders.standaloneSetup(new PolicyController(policies))
                .setControllerAdvice(new TourGlobalExceptionHandler(), new GlobalExceptionHandler()).build();

        otherMvc.perform(get("/api/v1/admin/policies/1")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
    }

    private void rejectScheduleChanges() throws Exception {
        mvc.perform(put(SCHEDULES + "/5").contentType(MediaType.APPLICATION_JSON).content(SCHEDULE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_NOT_EDITABLE"));
        mvc.perform(delete(SCHEDULES + "/5")).andExpect(status().isConflict());
    }
}
