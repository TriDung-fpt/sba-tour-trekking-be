package com.trekking.app.features.policy.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.policy.dto.response.PolicyDocumentResponse;
import com.trekking.app.features.policy.service.PolicyDocumentService;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PolicyDocumentServiceImpl implements PolicyDocumentService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final AuthorizationService authorization;

    public PolicyDocumentServiceImpl(AuthorizationService authorization) { this.authorization = authorization; }

    @Override
    public PolicyDocumentResponse extract(MultipartFile file) {
        authorization.require(Permission.POLICY_MANAGE);
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid("File must be between 1 byte and 5 MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            String content;
            if (name.endsWith(".pdf")) {
                try (var document = Loader.loadPDF(bytes)) {
                    if (document.getNumberOfPages() > 200) throw invalid("PDF must not exceed 200 pages");
                    content = new PDFTextStripper().getText(document);
                }
            } else if (name.endsWith(".docx")) {
                try (var document = new XWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new XWPFWordExtractor(document)) {
                    content = extractor.getText();
                }
            } else if (name.endsWith(".doc")) {
                try (var document = new HWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new WordExtractor(document)) {
                    content = extractor.getText();
                }
            } else throw invalid("Supported formats are PDF, DOC and DOCX");
            content = content.trim();
            if (content.isEmpty()) throw invalid("No text found; scanned PDF files require OCR");
            if (content.length() > 100000) throw invalid("Document text exceeds 100000 characters");
            return new PolicyDocumentResponse(content);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("Unable to read document; check file format and encryption");
        }
    }

    private static AuthException invalid(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "POLICY_DOCUMENT_INVALID", message);
    }
}
