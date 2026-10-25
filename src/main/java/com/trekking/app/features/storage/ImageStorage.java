package com.trekking.app.features.storage;

import org.springframework.web.multipart.MultipartFile;

/** Storage contract shared by features that upload images. */
public interface ImageStorage {
    /** Uploads an image under a caller-selected folder and returns its public URL and opaque delete key. */
    StoredImage upload(MultipartFile file, String folder);

    /** Deletes an image using the opaque key returned by {@link #upload(MultipartFile, String)}. */
    void delete(String key);

    /** Best-effort delete for cleanup after a later operation fails. */
    void cleanup(String key);
}
