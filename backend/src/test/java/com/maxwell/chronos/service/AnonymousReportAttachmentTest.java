package com.maxwell.chronos.service;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AnonymousReportAttachmentTest {
    @Test void pdfAuthorCommentsAndMetadataNeverReachDownload() throws Exception {
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(); pdf.addPage(page);
            pdf.getDocumentInformation().setAuthor("Private Employee");
            pdf.getDocumentInformation().setTitle("Private Employee File");
            var metadata = new PDMetadata(pdf);
            metadata.importXMPMetadata("<author>Private Employee</author>".getBytes(StandardCharsets.UTF_8));
            pdf.getDocumentCatalog().setMetadata(metadata);
            var note = new PDAnnotationText(); note.setTitlePopup("Private Employee"); note.setContents("Secret author comment");
            page.getAnnotations().add(note);
            pdf.save(original);
        }
        var sanitized = AnonymousReportAttachment.sanitize("Private Employee.pdf", original.toByteArray(), "safe-id");
        assertEquals("attachment-safe-id.pdf", sanitized.filename());
        try (PDDocument clean = PDDocument.load(sanitized.content())) {
            assertNull(clean.getDocumentInformation().getAuthor());
            assertNull(clean.getDocumentInformation().getTitle());
            assertNull(clean.getDocumentCatalog().getMetadata());
            assertTrue(clean.getPage(0).getAnnotations().isEmpty());
            assertEquals(1, clean.getNumberOfPages());
        }
        assertFalse(new String(sanitized.content(), StandardCharsets.ISO_8859_1).contains("Private Employee"));
    }
    @Test void imageTextMetadataAndOriginalFilenameAreRemoved() throws Exception {
        var image = new BufferedImage(5,5,BufferedImage.TYPE_INT_RGB);
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
        var root = new IIOMetadataNode("javax_imageio_png_1.0");
        var text = new IIOMetadataNode("tEXt");
        var entry = new IIOMetadataNode("tEXtEntry");
        entry.setAttribute("keyword","Author"); entry.setAttribute("value","Private Employee");
        text.appendChild(entry); root.appendChild(text); metadata.mergeTree("javax_imageio_png_1.0", root);
        var original = new ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(original)) {
            writer.setOutput(stream); writer.write(null, new IIOImage(image,null,metadata),null);
        } finally { writer.dispose(); }
        assertTrue(new String(original.toByteArray(), StandardCharsets.ISO_8859_1).contains("Private Employee"));
        var sanitized = AnonymousReportAttachment.sanitize("Private Employee.png", original.toByteArray(), "safe-id");
        assertFalse(new String(sanitized.content(), StandardCharsets.ISO_8859_1).contains("Private Employee"));
        assertEquals("attachment-safe-id.png", sanitized.filename());
        assertEquals(5, ImageIO.read(new ByteArrayInputStream(sanitized.content())).getWidth());
    }
    @Test void documentsAndDisguisedBinaryFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> AnonymousReportAttachment.sanitize("employee.docx", new byte[]{1}, "id"));
        assertThrows(IllegalArgumentException.class, () -> AnonymousReportAttachment.sanitize("employee.txt", new byte[]{0,1,2}, "id"));
        assertThrows(IllegalArgumentException.class, () -> AnonymousReportAttachment.sanitize("employee.jpg", "not an image".getBytes(), "id"));
    }
}
