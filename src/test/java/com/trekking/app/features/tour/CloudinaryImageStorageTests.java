package com.trekking.app.features.tour;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.trekking.app.features.tour.config.CloudinaryProperties;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.storage.CloudinaryImageStorage;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryImageStorageTests {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryImageStorage storage;

    @BeforeEach
    void setup() throws IOException {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            return Map.of("public_id", options.get("public_id"), "secure_url", "https://res.cloudinary.com/demo/image.png");
        });
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));
        storage = new CloudinaryImageStorage(cloudinary, new CloudinaryProperties("demo", "key", "secret"));
    }

    @Test
    void uploadsImageBytesUsingServerGeneratedPublicId() {
        var result = storage.upload(3L, png());
        assertThat(result.publicId()).startsWith("trekking/tours/3/");
        assertThat(result.url()).startsWith("https://");
    }

    @Test
    void rejectsEmptyAndDisguisedNonImageFilesBeforeNetworkCall() {
        var fake = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not an image".getBytes());
        assertThatThrownBy(() -> storage.upload(3L, fake)).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> storage.upload(3L, new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(TourException.class);
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rejectsOversizedFilesAndMissingConfigurationBeforeNetworkCall() {
        var large = mock(MultipartFile.class);
        when(large.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> storage.upload(3L, large)).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        var disabled = new CloudinaryImageStorage(cloudinary, new CloudinaryProperties("", "", ""));
        assertThatThrownBy(() -> disabled.upload(3L, png())).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("IMAGE_STORAGE_NOT_CONFIGURED"));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void cloudinaryImageDecodeErrorIs400AndAuthenticationErrorIs503() throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("error", Map.of("http_code", 400)));
        assertThatThrownBy(() -> storage.upload(3L, png())).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("error", Map.of("http_code", 401)));
        assertThatThrownBy(() -> storage.upload(3L, png())).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void networkFailureAttemptsCleanupAndReturns503() throws IOException {
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("timeout"));
        assertThatThrownBy(() -> storage.upload(3L, png())).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(uploader).destroy(startsWith("trekking/tours/3/"), anyMap());
    }

    @Test
    void malformedUploadResponseAttemptsCleanup() throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "http://invalid"));
        assertThatThrownBy(() -> storage.upload(3L, png())).isInstanceOf(TourException.class);
        verify(uploader).destroy(startsWith("trekking/tours/3/"), anyMap());
    }

    @Test
    void deleteTreatsAlreadyRemovedImageAsSuccessAndReportsFailure() throws IOException {
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));
        assertThatCode(() -> storage.delete("trekking/tours/3/image")).doesNotThrowAnyException();
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new IOException("offline"));
        assertThatThrownBy(() -> storage.delete("trekking/tours/3/image")).isInstanceOfSatisfying(TourException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatCode(() -> storage.cleanup("trekking/tours/3/image")).doesNotThrowAnyException();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "image.png", "image/png", PNG);
    }
}
