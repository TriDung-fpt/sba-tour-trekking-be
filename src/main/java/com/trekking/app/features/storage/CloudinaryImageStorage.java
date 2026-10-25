package com.trekking.app.features.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.trekking.app.features.storage.config.CloudinaryProperties;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class CloudinaryImageStorage implements ImageStorage {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryImageStorage(@Qualifier("cloudinary") Cloudinary cloudinary, CloudinaryProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public StoredImage upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw invalidImage();
        if (file.getSize() > MAX_BYTES) {
            throw new ImageStorageException(ImageStorageException.Reason.FILE_TOO_LARGE, "File must not exceed 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw invalidImage();
        }
        if (!hasImageHeader(bytes)) throw invalidImage();
        requireConfiguration();

        String key = folder.replaceAll("/+$", "") + "/" + UUID.randomUUID();
        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "resource_type", "image", "public_id", key, "overwrite", false,
                    "allowed_formats", new String[]{"jpg", "png", "webp"}, "return_error", true));
        } catch (IOException | RuntimeException ex) {
            // The remote upload may have completed even if its response was lost.
            cleanup(key);
            throw unavailable();
        }
        if (result == null) {
            cleanup(key);
            throw unavailable();
        }
        if (result.get("error") instanceof Map<?, ?> error) {
            if (error.get("http_code") instanceof Number code && code.intValue() == 400) throw invalidImage();
            throw unavailable();
        }
        if (!(result.get("secure_url") instanceof String url) || !url.startsWith("https://")
                || !key.equals(result.get("public_id"))) {
            cleanup(key);
            throw unavailable();
        }
        return new StoredImage(url, key);
    }

    @Override
    public void delete(String key) {
        requireConfiguration();
        try {
            var result = cloudinary.uploader().destroy(key, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
                throw new IllegalStateException("Unexpected Cloudinary delete response");
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("Cloud image deletion failed; key={}", key);
            throw unavailable();
        }
    }

    @Override
    public void cleanup(String key) {
        try {
            delete(key);
        } catch (RuntimeException ex) {
            log.warn("Cloud image needs manual cleanup; key={}", key);
        }
    }

    private void requireConfiguration() {
        if (!properties.configured()) {
            throw new ImageStorageException(ImageStorageException.Reason.NOT_CONFIGURED,
                    "Image storage credentials are not configured");
        }
    }

    private static boolean hasImageHeader(byte[] bytes) {
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        boolean webp = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return jpeg || png || webp;
    }

    private static ImageStorageException invalidImage() {
        return new ImageStorageException(ImageStorageException.Reason.INVALID_IMAGE,
                "File must be a non-empty JPEG, PNG or WebP image");
    }

    private static ImageStorageException unavailable() {
        return new ImageStorageException(ImageStorageException.Reason.UNAVAILABLE,
                "Image storage is temporarily unavailable");
    }
}
