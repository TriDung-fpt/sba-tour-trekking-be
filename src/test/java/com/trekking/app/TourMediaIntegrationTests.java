package com.trekking.app;

import com.trekking.app.features.tour.dto.request.ReorderTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UploadTourMediaRequest;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.TourMediaRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.service.TourMediaService;
import com.trekking.app.features.tour.storage.CloudinaryImageStorage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TourMediaIntegrationTests extends AuthTestInfrastructure {
    @Autowired TourMediaService service;
    @Autowired TourRepository tours;
    @Autowired TourMediaRepository media;
    @MockitoBean CloudinaryImageStorage storage;
    private long tourId;

    @BeforeEach
    void createTour() {
        var tour = new Tour();
        tour.setTitle("Images " + UUID.randomUUID());
        tourId = tours.saveAndFlush(tour).getId();
        when(storage.upload(eq(tourId), any())).thenAnswer(invocation -> new CloudinaryImageStorage.UploadedImage(
                "test/" + UUID.randomUUID(), "https://example.invalid/image.jpg"));
    }

    @AfterEach
    void removeTour() {
        media.deleteAll(media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId));
        media.flush();
        tours.deleteById(tourId);
    }

    @Test
    void coverConstraintOrderAndCloudinaryFailureRollbackWorkAgainstPostgres() {
        var first = service.upload(tourId, request());
        var second = service.upload(tourId, request());
        service.setCover(tourId, second.id());
        service.reorder(tourId, new ReorderTourMediaRequest(List.of(second.id(), first.id())));
        var beforeDelete = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId);
        assertThat(beforeDelete).extracting(image -> image.getId()).containsExactly(second.id(), first.id());
        assertThat(beforeDelete.stream().filter(image -> image.getIsCover()).count()).isEqualTo(1);
        String publicId = media.findById(second.id()).orElseThrow().getCloudinaryPublicId();
        doThrow(new TourException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE", "offline"))
                .when(storage).delete(publicId);

        assertThatThrownBy(() -> service.delete(tourId, second.id())).isInstanceOf(TourException.class);
        assertThat(media.findById(second.id()).orElseThrow().getIsCover()).isTrue();
        assertThat(media.findById(first.id()).orElseThrow().getIsCover()).isFalse();

        doNothing().when(storage).delete(publicId);
        service.delete(tourId, second.id());
        assertThat(media.existsById(second.id())).isFalse();
        assertThat(media.findById(first.id()).orElseThrow().getIsCover()).isTrue();
    }

    @Test
    void databaseConstraintFailureRollsBackMetadataAndCleansUpUploadedFile() {
        when(storage.upload(eq(tourId), any())).thenReturn(new CloudinaryImageStorage.UploadedImage("test/failed", null));
        assertThatThrownBy(() -> service.upload(tourId, request())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId)).isEmpty();
        verify(storage).cleanup("test/failed");
    }

    private static UploadTourMediaRequest request() {
        return new UploadTourMediaRequest(new MockMultipartFile("file", "image.jpg", "image/jpeg", new byte[]{1}), null);
    }
}
