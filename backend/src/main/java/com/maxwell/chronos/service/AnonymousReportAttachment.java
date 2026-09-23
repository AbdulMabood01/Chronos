package com.maxwell.chronos.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Produces fresh visible-content-only files; never forwards anonymous original metadata. */
final class AnonymousReportAttachment {
    private AnonymousReportAttachment() {}
    static String extension(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    static boolean supported(String name) {
        return java.util.Set.of("png", "jpg", "jpeg", "gif", "pdf", "txt").contains(extension(name));
    }
    static EmployeeReportService.Download sanitize(String name, byte[] bytes, String reference) {
        if (!supported(name)) throw new IllegalArgumentException("Anonymous attachments must be PNG, JPG, GIF, PDF or plain UTF-8 text. Export documents as PDF or images first.");
        try {
            String ext = extension(name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (ext.equals("txt")) {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", "");
                if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                    throw new IOException("Not plain text");
                output.write(text.getBytes(StandardCharsets.UTF_8));
            } else if (ext.equals("pdf")) {
                try (PDDocument original = PDDocument.load(bytes); PDDocument clean = new PDDocument()) {
                    if (original.isEncrypted() || original.getNumberOfPages() < 1 || original.getNumberOfPages() > 30)
                        throw new IOException("Unsupported PDF");
                    PDFRenderer renderer = new PDFRenderer(original);
                    // Do not render popup comments or other annotation appearances into the clean copy.
                    renderer.setAnnotationsFilter(annotation -> false);
                    long totalPixels = 0;
                    for (int i=0; i<original.getNumberOfPages(); i++) {
                        PDRectangle bounds = original.getPage(i).getCropBox();
                        double pixels = Math.ceil(bounds.getWidth() * 120 / 72) * Math.ceil(bounds.getHeight() * 120 / 72);
                        if (!Double.isFinite(pixels) || pixels < 1 || pixels > 16000000 || (totalPixels += (long)pixels) > 40000000)
                            throw new IOException("PDF too large to sanitize");
                        BufferedImage raster = renderer.renderImageWithDPI(i, 120);
                        PDPage page = new PDPage(new PDRectangle(raster.getWidth() * 72f / 120, raster.getHeight() * 72f / 120));
                        clean.addPage(page);
                        try (PDPageContentStream stream = new PDPageContentStream(clean, page)) {
                            stream.drawImage(LosslessFactory.createFromImage(clean, raster), 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                        }
                        raster.flush();
                    }
                    clean.save(output);
                }
            } else {
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IOException("Unsupported image");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        if ((long)reader.getWidth(0) * reader.getHeight(0) > 16000000) throw new IOException("Image too large");
                        if (reader.getNumImages(true) != 1) throw new IOException("Use a still image");
                        BufferedImage source = reader.read(0);
                        BufferedImage clean = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        var graphics = clean.createGraphics();
                        try { graphics.drawImage(source, 0, 0, null); } finally { graphics.dispose(); }
                        ImageIO.write(clean, "png", output);
                        source.flush(); clean.flush();
                        ext = "png";
                    } finally { reader.dispose(); }
                }
            }
            if (output.size() < 1 || output.size() > 10 * 1024 * 1024) throw new IOException("Sanitized attachment too large");
            return new EmployeeReportService.Download("attachment-" + reference + "." + ext, output.toByteArray());
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Unable to remove metadata safely. Use a smaller, unencrypted PDF (up to 30 pages), a still PNG/JPG/GIF, or plain UTF-8 text.", e);
        }
    }
}
