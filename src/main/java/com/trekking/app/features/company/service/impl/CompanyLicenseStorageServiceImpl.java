package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.company.dto.response.CompanyLicenseResponse;
import com.trekking.app.features.company.service.CompanyLicenseStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Private evidence store; generated references are bound to a company in PostgreSQL. */
@Service
public class CompanyLicenseStorageServiceImpl implements CompanyLicenseStorageService {
    private static final Logger log = LoggerFactory.getLogger(CompanyLicenseStorageServiceImpl.class);
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Path directory;

    public CompanyLicenseStorageServiceImpl(
            @Value("${app.company.license-directory:data/company-licenses}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid();
        try (var stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
            String extension = validatedExtension(bytes, file.getContentType());
            Files.createDirectories(directory);
            String reference = UUID.randomUUID() + extension;
            Files.write(resolve(reference), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return reference;
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    @Override
    public CompanyLicenseResponse read(String reference) {
        Path path = resolve(reference);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw missing();
            try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_BYTES) throw missing();
                return new CompanyLicenseResponse(bytes, "license" + reference.substring(reference.lastIndexOf('.')));
            }
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String reference) {
        if (reference == null) return;
        try {
            Files.deleteIfExists(resolve(reference));
        } catch (IOException | AuthException ex) {
            // Cleanup failure cannot change the committed outcome; operators can remove orphan files.
            log.warn("Unable to clean up obsolete company license evidence");
        }
    }

    private Path resolve(String reference) {
        if (reference == null || !reference.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|png|jpg)")) {
            throw missing();
        }
        return directory.resolve(reference);
    }

    private String validatedExtension(byte[] bytes, String contentType) {
        if ("application/pdf".equals(contentType) && bytes.length >= 12
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length),
                        StandardCharsets.US_ASCII).contains("%%EOF")) return ".pdf";
        byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        boolean isPng = "image/png".equals(contentType) && bytes.length >= 8
                && Arrays.equals(Arrays.copyOf(bytes, 8), png);
        boolean isJpeg = "image/jpeg".equals(contentType) && bytes.length >= 4
                && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        if (!isPng && !isJpeg) throw invalid();
        try (var image = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(image);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 20_000_000 || reader.read(0) == null) throw invalid();
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw invalid();
        }
        return isPng ? ".png" : ".jpg";
    }

    private static AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_COMPANY_LICENSE",
                "Upload a valid PDF, PNG or JPEG file of at most 5 MiB");
    }

    private static AuthException missing() {
        return new AuthException(HttpStatus.NOT_FOUND, "COMPANY_LICENSE_NOT_FOUND", "Company license was not found");
    }

    private static AuthException unavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "COMPANY_LICENSE_UNAVAILABLE", "Company license storage is unavailable");
    }
}
