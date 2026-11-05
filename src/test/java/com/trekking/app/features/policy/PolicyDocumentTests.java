package com.trekking.app.features.policy;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.policy.service.impl.PolicyDocumentServiceImpl;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyDocumentTests {
    private final PolicyDocumentServiceImpl service = new PolicyDocumentServiceImpl(mock(AuthorizationService.class));

    @Test
    void extractsPdfText() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText("Cancellation terms");
                stream.endText();
            }
            document.save(bytes);
        }
        var file = new MockMultipartFile("file", "terms.pdf", "application/pdf", bytes.toByteArray());
        assertThat(service.extract(file).content()).contains("Cancellation terms");
    }

    @Test
    void extractsDocxText() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Cancellation terms");
            document.write(bytes);
        }
        var file = new MockMultipartFile("file", "terms.docx", "application/octet-stream", bytes.toByteArray());
        assertThat(service.extract(file).content()).contains("Cancellation terms");
    }

    @Test
    void rejectsInvalidOrUnsupportedFile() {
        assertThatThrownBy(() -> service.extract(new MockMultipartFile("file", "terms.pdf", "application/pdf", "not a pdf".getBytes())))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.extract(new MockMultipartFile("file", "terms.txt", "text/plain", "terms".getBytes())))
                .isInstanceOf(AuthException.class);
    }
}
