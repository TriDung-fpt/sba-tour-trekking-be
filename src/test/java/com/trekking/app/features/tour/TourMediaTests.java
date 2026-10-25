package com.trekking.app.features.tour;

import com.trekking.app.core.exception.GlobalExceptionHandler;
import com.trekking.app.features.tour.controller.TourMediaController;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourMedia;
import com.trekking.app.features.tour.enums.MediaType;
import com.trekking.app.features.tour.exception.TourGlobalExceptionHandler;
import com.trekking.app.features.tour.mapper.TourMapper;
import com.trekking.app.features.tour.repository.TourMediaRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.service.impl.TourMediaServiceImpl;
import com.trekking.app.features.storage.ImageStorage;
import com.trekking.app.features.storage.ImageStorageException;
import com.trekking.app.features.storage.StoredImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TourMediaTests {
    private static final String PATH = "/api/v1/tours/1/media";
    private final List<TourMedia> images = new ArrayList<>();
    private TourRepository tours;
    private TourMediaRepository media;
    private ImageStorage storage;
    private PlatformTransactionManager transactions;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        tours = mock(TourRepository.class);
        media = mock(TourMediaRepository.class);
        storage = mock(ImageStorage.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(tours.existsById(1L)).thenReturn(true);
        when(tours.findLockedById(1L)).thenReturn(Optional.of(new Tour()));
        when(storage.upload(any(), eq("trekking/tours/1"))).thenReturn(new StoredImage(
                "https://res.cloudinary.com/demo/image/upload/new.jpg", "trekking/tours/1/new"));
        when(media.findAllByTourIdOrderByDisplayOrderAscIdAsc(1L)).thenAnswer(invocation -> images.stream()
                .sorted(Comparator.comparing(TourMedia::getDisplayOrder).thenComparing(TourMedia::getId)).toList());
        when(media.findByIdAndTourIdAndMediaType(anyLong(), eq(1L), eq(MediaType.IMAGE)))
                .thenAnswer(invocation -> images.stream().filter(image -> image.getId().equals(invocation.getArgument(0)))
                        .findFirst());
        when(media.saveAndFlush(any(TourMedia.class))).thenAnswer(invocation -> {
            TourMedia image = invocation.getArgument(0);
            if (image.getId() == null) {
                ReflectionTestUtils.setField(image, "id", 100L);
                images.add(image);
            }
            return image;
        });
        doAnswer(invocation -> images.remove(invocation.getArgument(0))).when(media).delete(any(TourMedia.class));
        var service = new TourMediaServiceImpl(tours, media, Mappers.getMapper(TourMapper.class), storage, transactions);
        mvc = MockMvcBuilders.standaloneSetup(new TourMediaController(service))
                .setControllerAdvice(new TourGlobalExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test
    void uploadSavesStorageKeyAndSelectsFirstImageAsCover() throws Exception {
        mvc.perform(multipart(PATH).file(file()).param("caption", "  Summit  "))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isCover").value(true))
                .andExpect(jsonPath("$.displayOrder").value(0)).andExpect(jsonPath("$.caption").value("Summit"))
                .andExpect(jsonPath("$.cloudinaryPublicId").doesNotExist());
        assertThat(images.getFirst().getStorageKey()).isEqualTo("trekking/tours/1/new");
        verify(transactions).commit(any());
    }

    @Test
    void uploadAppendsWithoutReplacingExistingCover() throws Exception {
        images.add(image(2, 7, true, null));
        mvc.perform(multipart(PATH).file(file()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isCover").value(false))
                .andExpect(jsonPath("$.displayOrder").value(8));
        assertThat(images.getFirst().getIsCover()).isTrue();
    }

    @Test
    void changingCoverClearsOldCoverBeforeSavingNewOne() throws Exception {
        var oldCover = image(2, 0, true, null);
        var newCover = image(3, 1, false, null);
        images.addAll(List.of(oldCover, newCover));
        when(media.saveAllAndFlush(any())).thenAnswer(invocation -> {
            assertThat(oldCover.getIsCover()).isFalse();
            assertThat(newCover.getIsCover()).isFalse();
            return List.of();
        });
        mvc.perform(patch(PATH + "/3/cover"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isCover").value(true));
        var order = inOrder(media);
        order.verify(media).saveAllAndFlush(any());
        order.verify(media).saveAndFlush(newCover);
        assertThat(oldCover.getIsCover()).isFalse();
    }

    @Test
    void captionCanBeEditedAndClearedWithoutChangingCloudinaryFile() throws Exception {
        images.add(image(2, 0, true, null));
        mvc.perform(patch(PATH + "/2").contentType("application/json").content("{\"caption\":\"  Camp  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caption").value("Camp"));
        mvc.perform(patch(PATH + "/2").contentType("application/json").content("{\"caption\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caption").doesNotExist());
        verifyNoInteractions(storage);
    }

    @Test
    void reorderRequiresCompleteUniqueIdsFromThisTour() throws Exception {
        images.addAll(List.of(image(2, 0, true, null), image(3, 1, false, null)));
        for (String body : List.of("{\"mediaIds\":[2,2]}", "{\"mediaIds\":[2]}", "{\"mediaIds\":[2,99]}",
                "{\"mediaIds\":null}", "{\"mediaIds\":[null,3]}")) {
            mvc.perform(put(PATH + "/order").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(PATH + "/order").contentType("application/json").content("{\"mediaIds\":[3,2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].displayOrder").value(0)).andExpect(jsonPath("$[1].displayOrder").value(1));
    }

    @Test
    void deleteCoverPromotesFirstRemainingImageAndDeletesStoredFileAfterFlush() throws Exception {
        var cover = image(2, 0, true, "trekking/tours/1/cover");
        var next = image(3, 1, false, null);
        images.addAll(List.of(cover, next));
        mvc.perform(delete(PATH + "/2")).andExpect(status().isNoContent());
        assertThat(next.getIsCover()).isTrue();
        var order = inOrder(media, storage);
        order.verify(media).delete(cover);
        order.verify(media).flush();
        order.verify(media).saveAndFlush(next);
        order.verify(storage).delete("trekking/tours/1/cover");
    }

    @Test
    void legacyImageDeleteSkipsStorageAndLastImageLeavesNoCover() throws Exception {
        images.add(image(2, 0, true, null));
        mvc.perform(delete(PATH + "/2")).andExpect(status().isNoContent());
        assertThat(images).isEmpty();
        verifyNoInteractions(storage);
    }

    @Test
    void missingTourOrImageReturns404AndDoesNotCallStorage() throws Exception {
        when(tours.existsById(1L)).thenReturn(false);
        mvc.perform(multipart(PATH).file(file())).andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/99")).andExpect(status().isNotFound());
        when(tours.findLockedById(1L)).thenReturn(Optional.empty());
        mvc.perform(patch(PATH + "/99/cover")).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test
    void storageFailureReturns503WithoutSavingUpload() throws Exception {
        when(storage.upload(any(), eq("trekking/tours/1"))).thenThrow(storageFailure());
        mvc.perform(multipart(PATH).file(file())).andExpect(status().isServiceUnavailable());
        verify(media, never()).saveAndFlush(any());
    }

    @Test
    void failedDatabaseSaveOrCommitCleansUpTheUploadedImage() throws Exception {
        when(media.saveAndFlush(any(TourMedia.class))).thenThrow(new DataIntegrityViolationException("constraint"));
        mvc.perform(multipart(PATH).file(file())).andExpect(status().isConflict());
        verify(storage).cleanup("trekking/tours/1/new");
        reset(media);
        when(media.findAllByTourIdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of());
        when(media.saveAndFlush(any(TourMedia.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataAccessResourceFailureException("commit failed")).when(transactions).commit(any());
        mvc.perform(multipart(PATH).file(file())).andExpect(status().isServiceUnavailable());
        verify(storage, times(2)).cleanup("trekking/tours/1/new");
    }

    @Test
    void storageDeleteFailureIsReported() throws Exception {
        images.add(image(2, 0, true, "trekking/tours/1/image"));
        doThrow(storageFailure()).when(storage).delete("trekking/tours/1/image");
        mvc.perform(delete(PATH + "/2")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void uploadRequiresAFilePart() throws Exception {
        mvc.perform(multipart(PATH)).andExpect(status().isBadRequest());
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private static TourMedia image(long id, int order, boolean cover, String publicId) {
        var image = new TourMedia();
        ReflectionTestUtils.setField(image, "id", id);
        image.setTourId(1L);
        image.setMediaType(MediaType.IMAGE);
        image.setUrl("https://example.invalid/" + id + ".jpg");
        image.setDisplayOrder(order);
        image.setIsCover(cover);
        image.setStorageKey(publicId);
        return image;
    }

    private static ImageStorageException storageFailure() {
        return new ImageStorageException(ImageStorageException.Reason.UNAVAILABLE, "Image storage unavailable");
    }
}
