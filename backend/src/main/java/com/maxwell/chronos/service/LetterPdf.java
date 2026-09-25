package com.maxwell.chronos.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.List;

/** Company letterhead with complete, flowing text and repeated page identification. */
final class LetterPdf implements AutoCloseable {
    private static final Color NAVY = new Color(16, 51, 71), WINE = new Color(130, 6, 56), MUTED = new Color(92, 109, 121);
    private static final float LEFT = 54, WIDTH = 504, BOTTOM = 90;
    private final PDDocument document = new PDDocument();
    private final String reference;
    private final boolean approved;
    private final PDImageXObject logo;
    private final PDImageXObject managerSignature;
    private PDPageContentStream content;
    private float y;

    private LetterPdf(String reference, boolean approved) throws IOException {
        this.reference = reference;
        this.approved = approved;
        try (InputStream in = LetterPdf.class.getResourceAsStream("/Logo.png")) {
            if (in == null) throw new IOException("Company logo resource is missing");
            logo = PDImageXObject.createFromByteArray(document, in.readAllBytes(), "Maxwell Network Inc");
        } catch (IOException | RuntimeException ex) { document.close(); throw ex; }
        try (InputStream in = LetterPdf.class.getResourceAsStream("/manager-signature.png")) {
            if (in == null) throw new IOException("Manager signature resource is missing");
            managerSignature = PDImageXObject.createFromByteArray(document, in.readAllBytes(), "Manager signature");
        } catch (IOException | RuntimeException ex) { document.close(); throw ex; }
    }

    static byte[] render(String title, String reference, boolean approved, String letter) {
        try (LetterPdf pdf = new LetterPdf(reference, approved)) {
            pdf.document.getDocumentInformation().setTitle(title + " | Maxwell Network Inc.");
            pdf.document.getDocumentInformation().setAuthor("Maxwell Network Inc.");
            pdf.document.getDocumentInformation().setSubject(approved ? "Approved employee letter" : "Preview - not approved");
            pdf.page();
            String[] sections = letter.split("\\R\\s*\\R");
            String[] opening = sections[0].split("\\R");
            pdf.text("REFERENCE  " + reference, LEFT, 654, 8, PDType1Font.HELVETICA_BOLD, MUTED);
            pdf.right(opening[opening.length - 1], 558, 654, 9, PDType1Font.HELVETICA, MUTED);
            pdf.y = 623;
            pdf.paragraph(title, PDType1Font.HELVETICA_BOLD, 19, 24, NAVY);
            pdf.y -= 7;
            pdf.paragraph("To whom it may concern,", PDType1Font.TIMES_ROMAN, 11, 15, NAVY);
            for (int i = 1; i < sections.length; i++) {
                String section = sections[i].strip();
                if (section.isEmpty() || section.startsWith("Subject:") || section.toLowerCase(Locale.ROOT).startsWith("to whom")) continue;
                if (section.startsWith("Employer Information")) {
                    pdf.employer(section);
                } else if (section.startsWith("tech.maxwellnetwork.org")) {
                    // Company contact details repeat in the footer of every page.
                } else if (section.startsWith("Best Regards,")) {
                    pdf.ensure(112);
                    float signatureHeight = 48f;
                    float signatureWidth = signatureHeight * pdf.managerSignature.getWidth() / pdf.managerSignature.getHeight();
                    pdf.content.drawImage(pdf.managerSignature, LEFT, pdf.y - signatureHeight, signatureWidth, signatureHeight);
                    pdf.y -= signatureHeight + 8;
                    pdf.text("Best regards,", LEFT, pdf.y, 10.5f, PDType1Font.TIMES_ROMAN, NAVY);
                    pdf.y -= 18;
                    String[] signature = section.split("\\R");
                    for (int n = 1; n < signature.length; n++) {
                        pdf.text(signature[n], LEFT, pdf.y, n == 1 ? 11 : 9, n == 1 ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, n == 1 ? NAVY : MUTED);
                        pdf.y -= 15;
                    }
                    pdf.y -= 5;
                } else {
                    pdf.paragraph(section, PDType1Font.TIMES_ROMAN, 11, 14.5f, NAVY);
                }
            }
            return pdf.finish();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    private void page() throws IOException {
        if (content != null) content.close();
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        fill(0, 784, 612, 8, WINE);
        content.drawImage(logo, 47, 694, 135, 135f * logo.getHeight() / logo.getWidth());
        right("MAXWELL NETWORK INC.", 558, 748, 11, PDType1Font.HELVETICA_BOLD, NAVY);
        right("Human Resources", 558, 729, 9, PDType1Font.HELVETICA, MUTED);
        right("hr@maxwellnetwork.org  |  (872) 999-2576", 558, 712, 8, PDType1Font.HELVETICA, MUTED);
        fill(LEFT, 677, WIDTH, .7f, new Color(213, 219, 224));
        fill(LEFT, 677, 48, 2, WINE);
        if (!approved) text("PREVIEW - NOT APPROVED", LEFT, 690, 8, PDType1Font.HELVETICA_BOLD, WINE);
        y = 650;
        if (document.getNumberOfPages() > 1) {
            text("CONTINUED  |  " + reference, LEFT, y, 8, PDType1Font.HELVETICA_BOLD, MUTED);
            y -= 26;
        }
    }

    private void paragraph(String value, PDType1Font font, float size, float leading, Color color) throws IOException {
        List<String> lines = wrap(value, font, size, WIDTH);
        // Avoid starting a paragraph with a single line at the bottom of a page.
        ensure(Math.min(lines.size(), 2) * leading + 8);
        for (String line : lines) {
            ensure(leading);
            text(line, LEFT, y, size, font, color);
            y -= leading;
        }
        y -= 10;
    }

    private void employer(String section) throws IOException {
        String[] rows = section.split("\\R");
        // Retain the company's existing verification identifiers and contact details.
        List<String> details = new ArrayList<>();
        for (int i = 1; i < rows.length; i++) details.addAll(wrap(rows[i], PDType1Font.HELVETICA, 8, WIDTH - 26));
        float height = 32 + details.size() * 11;
        ensure(height + 8);
        fill(LEFT, y - height, WIDTH, height, new Color(245, 247, 249));
        fill(LEFT, y - height, 2, height, WINE);
        text("EMPLOYER INFORMATION", LEFT + 13, y - 17, 8, PDType1Font.HELVETICA_BOLD, NAVY);
        y -= 33;
        for (String line : details) {
            text(line, LEFT + 13, y, 8, PDType1Font.HELVETICA, MUTED);
            y -= 11;
        }
        y -= 12;
    }

    private byte[] finish() throws IOException {
        content.close(); content = null;
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            try (var footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                content = footer;
                fill(LEFT, 69, WIDTH, .7f, new Color(213, 219, 224));
                text("tech.maxwellnetwork.org", LEFT, 52, 8, PDType1Font.HELVETICA_BOLD, WINE);
                right("Page " + (i + 1) + " of " + document.getNumberOfPages(), 558, 52, 8, PDType1Font.HELVETICA, MUTED);
                text("5875 N Lincoln Ave, Suite LL26, Chicago, IL 60659-2122", LEFT, 37, 8, PDType1Font.HELVETICA, MUTED);
            } finally { content = null; }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); document.save(out); return out.toByteArray();
    }
    private void ensure(float height) throws IOException { if (y - height < BOTTOM) page(); }
    private void fill(float x, float bottom, float width, float height, Color color) throws IOException {
        content.setNonStrokingColor(color); content.addRect(x, bottom, width, height); content.fill();
    }
    private void text(String value, float x, float baseline, float size, PDType1Font font, Color color) throws IOException {
        content.beginText(); content.setNonStrokingColor(color); content.setFont(font, size);
        content.newLineAtOffset(x, baseline); content.showText(safe(value, font)); content.endText();
    }
    private void right(String value, float edge, float baseline, float size, PDType1Font font, Color color) throws IOException {
        text(value, edge - font.getStringWidth(safe(value, font)) / 1000 * size, baseline, size, font, color);
    }
    private static List<String> wrap(String value, PDType1Font font, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>(); String remaining = safe(value, font).strip();
        while (!remaining.isEmpty()) {
            int end = 0;
            while (end < remaining.length() && font.getStringWidth(remaining.substring(0, end + 1)) / 1000 * size <= width) end++;
            end = Math.max(1, end);
            if (end < remaining.length()) { int space = remaining.lastIndexOf(' ', end); if (space > 0) end = space; }
            lines.add(remaining.substring(0, end)); remaining = remaining.substring(end).strip();
        }
        return lines;
    }
    private static String safe(String value, PDType1Font font) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(code -> {
            if (Character.isWhitespace(code) || Character.isISOControl(code)) result.append(' ');
            else { String glyph = new String(Character.toChars(code)); try { font.encode(glyph); result.append(glyph); } catch (IOException | IllegalArgumentException ex) { result.append('?'); } }
        });
        return result.toString();
    }
    @Override public void close() throws IOException { try { if (content != null) content.close(); } finally { document.close(); } }
}
