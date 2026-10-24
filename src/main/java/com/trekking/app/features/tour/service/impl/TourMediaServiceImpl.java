package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.ReorderTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UploadTourMediaRequest;
import com.trekking.app.features.tour.dto.response.TourMediaResponse;
import com.trekking.app.features.tour.entity.TourMedia;
import com.trekking.app.features.tour.enums.MediaType;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.mapper.TourMapper;
import com.trekking.app.features.tour.repository.TourMediaRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.service.TourMediaService;
import com.trekking.app.features.tour.storage.CloudinaryImageStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TourMediaServiceImpl implements TourMediaService {
    private final TourRepository tours;
    private final TourMediaRepository media;
    private final TourMapper mapper;
    private final CloudinaryImageStorage storage;
    private final TransactionTemplate transactions;

    public TourMediaServiceImpl(TourRepository tours, TourMediaRepository media, TourMapper mapper,
                                CloudinaryImageStorage storage, PlatformTransactionManager transactionManager) {
        this.tours = tours;
        this.media = media;
        this.mapper = mapper;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public TourMediaResponse upload(long tourId, UploadTourMediaRequest request) {
        if (!tours.existsById(tourId)) {
            throw notFound("Tour");
        }
        var uploaded = storage.upload(tourId, request.file());
        try {
            // Catch failures at commit as well as flush, so the uploaded file can be removed.
            return transactions.execute(status -> {
                lockTour(tourId);
                var existing = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId);
                var image = new TourMedia();
                image.setTourId(tourId);
                image.setMediaType(MediaType.IMAGE);
                image.setUrl(uploaded.url());
                image.setCloudinaryPublicId(uploaded.publicId());
                image.setCaption(caption(request.caption()));
                image.setDisplayOrder(existing.stream().mapToInt(TourMedia::getDisplayOrder).max().orElse(-1) + 1);
                image.setIsCover(existing.stream().noneMatch(item -> Boolean.TRUE.equals(item.getIsCover())));
                return mapper.toResponse(media.saveAndFlush(image));
            });
        } catch (RuntimeException ex) {
            storage.cleanup(uploaded.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public TourMediaResponse updateCaption(long tourId, long mediaId, UpdateTourMediaRequest request) {
        lockTour(tourId);
        var image = requireImage(tourId, mediaId);
        image.setCaption(caption(request.caption()));
        return mapper.toResponse(media.saveAndFlush(image));
    }

    @Override
    @Transactional
    public TourMediaResponse setCover(long tourId, long mediaId) {
        lockTour(tourId);
        var image = requireImage(tourId, mediaId);
        if (!Boolean.TRUE.equals(image.getIsCover())) {
            var existing = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId);
            existing.forEach(item -> item.setIsCover(false));
            // Clear the current cover before setting the new one: uq_tour_cover is immediate.
            media.saveAllAndFlush(existing);
            image.setIsCover(true);
            media.saveAndFlush(image);
        }
        return mapper.toResponse(image);
    }

    @Override
    @Transactional
    public List<TourMediaResponse> reorder(long tourId, ReorderTourMediaRequest request) {
        lockTour(tourId);
        var images = media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream()
                .filter(item -> item.getMediaType() == MediaType.IMAGE).toList();
        var byId = images.stream().collect(Collectors.toMap(TourMedia::getId, Function.identity()));
        var ids = request.mediaIds();
        if (ids.size() != new HashSet<>(ids).size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_ORDER",
                    "mediaIds must contain every image of this tour exactly once",
                    Map.of("mediaIds", "Must contain every image of this tour exactly once, without duplicate IDs"));
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setDisplayOrder(i);
        }
        media.saveAllAndFlush(images);
        return ids.stream().map(id -> mapper.toResponse(byId.get(id))).toList();
    }

    @Override
    @Transactional
    public void delete(long tourId, long mediaId) {
        lockTour(tourId);
        var image = requireImage(tourId, mediaId);
        media.delete(image);
        media.flush();
        if (Boolean.TRUE.equals(image.getIsCover())) {
            media.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream()
                    .filter(item -> item.getMediaType() == MediaType.IMAGE).findFirst().ifPresent(next -> {
                        next.setIsCover(true);
                        media.saveAndFlush(next);
                    });
        }
        // Flush DB changes first; a Cloudinary failure rolls back deletion and cover changes.
        if (image.getCloudinaryPublicId() != null) {
            storage.delete(image.getCloudinaryPublicId());
        }
    }

    private TourMedia requireImage(long tourId, long mediaId) {
        return media.findByIdAndTourIdAndMediaType(mediaId, tourId, MediaType.IMAGE)
                .orElseThrow(() -> notFound("Image"));
    }

    private void lockTour(long tourId) {
        tours.findLockedById(tourId).orElseThrow(() -> notFound("Tour"));
    }

    private static String caption(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static TourException notFound(String resource) {
        return new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " was not found");
    }
}
