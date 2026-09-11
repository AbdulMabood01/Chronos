package com.maxwell.chronos.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Branded, paginated report. All user-supplied fields wrap to the available width. */
final class TimesheetPdf implements AutoCloseable {
    private static final Color NAVY = new Color(8, 43, 64);
    private static final Color BURGUNDY = new Color(125, 0, 51);
    private static final Color MUTED = new Color(85, 104, 116);
    private static final Color LIGHT = new Color(242, 246, 248);
    private static final float LEFT = 44, WIDTH = 524, BOTTOM = 66;
    private final PDDocument document = new PDDocument();
    private final String reference;
    private final String period;
    private final PDImageXObject logo;
    private PDPageContentStream content;
    private float y;
    private boolean table;
    private boolean projectColumn = true;
    private int rowIndex;

    TimesheetPdf(String reference, String period) throws IOException {
        this.reference = reference;
        this.period = period;
        try (InputStream in = TimesheetPdf.class.getResourceAsStream("/Logo.png")) {
            if (in == null) throw new IOException("Company logo is missing from report resources");
            logo = PDImageXObject.createFromByteArray(document, in.readAllBytes(), "Maxwell logo");
            document.getDocumentInformation().setTitle("Approved Timesheet | " + period);
            document.getDocumentInformation().setAuthor("Maxwell Network Inc.");
            newPage();
        } catch (IOException | RuntimeException ex) {
            document.close();
            throw ex;
        }
    }

    private void newPage() throws IOException {
        if (content != null) content.close();
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        fill(0, 784, 612, 8, BURGUNDY);
        float logoWidth = 134;
        float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
        content.drawImage(logo, LEFT, 768 - logoHeight, logoWidth, logoHeight);
        text("APPROVED TIMESHEET", 282, 740, 19, true, NAVY);
        text(period, 282, 718, 11, false, MUTED);
        text(reference, 282, 701, 9, false, MUTED);
        fill(LEFT, 678, WIDTH, 1, BURGUNDY);
        y = 660;
        if (table) tableHeader();
    }

    void section(String title) throws IOException {
        table = false;
        ensure(38);
        y -= 8;
        text(title.toUpperCase(java.util.Locale.ROOT), LEFT, y, 10, true, BURGUNDY);
        y -= 20;
    }

    void field(String label, String value) throws IOException {
        List<String> lines = wrap(value == null || value.isBlank() ? "Not recorded" : value, 374, 10, false);
        for (int i = 0; i < lines.size(); i++) {
            ensure(16);
            if (i == 0) text(label, LEFT, y, 9, true, MUTED);
            text(lines.get(i), 194, y, 10, false, NAVY);
            y -= 16;
        }
    }

    void beginEntries() throws IOException {
        section("Time entries");
        ensure(64);
        table = true;
        projectColumn = true;
        tableHeader();
    }

    void beginProjectEntries(String project) throws IOException {
        section(project);
        ensure(64);
        table = true;
        projectColumn = false;
        tableHeader();
    }

    void beginProjectSummary() throws IOException {
        section("Project summary");
        ensure(64);
        table = false;
        fill(LEFT, y - 22, WIDTH, 25, NAVY);
        text("PROJECT", 52, y - 13, 8, true, Color.WHITE);
        text("HOURS", 286, y - 13, 8, true, Color.WHITE);
        text("RATE", 365, y - 13, 8, true, Color.WHITE);
        text("AMOUNT", 486, y - 13, 8, true, Color.WHITE);
        y -= 22;
    }

    void projectSummary(String project, String hours, String rate, String amount) throws IOException {
        List<String> projects = wrap(project, 214, 9, false);
        int count = projects.size();
        float rowHeight = count * 12 + 12;
        if (rowHeight <= 572 && y - rowHeight < BOTTOM) newPage();
        fill(LEFT, y - rowHeight, WIDTH, rowHeight, rowIndex % 2 == 0 ? LIGHT : Color.WHITE);
        for (int i = 0; i < count; i++) {
            text(projects.get(i), 52, y - 16 - i * 12, 9, false, NAVY);
        }
        right(hours, 333, y - 16, 9, true, NAVY);
        right(rate, 438, y - 16, 9, false, MUTED);
        right(amount, 558, y - 16, 9, true, NAVY);
        y -= rowHeight;
        rowIndex++;
    }

    private void tableHeader() throws IOException {
        fill(LEFT, y - 22, WIDTH, 25, NAVY);
        text("DATE", 52, y - 13, 8, true, Color.WHITE);
        if (projectColumn) {
            text("PROJECT", 133, y - 13, 8, true, Color.WHITE);
            text("HOURS", 270, y - 13, 8, true, Color.WHITE);
            text("SESSIONS / NOTES", 320, y - 13, 8, true, Color.WHITE);
        } else {
            text("HOURS", 174, y - 13, 8, true, Color.WHITE);
            text("SESSIONS / NOTES", 235, y - 13, 8, true, Color.WHITE);
        }
        y -= 22;
    }

    void entry(String date, String project, String hours, String details) throws IOException {
        List<String> projects = wrap(project, 122, 9, false);
        List<String> notes = wrap(details, projectColumn ? 240 : 318, 9, false);
        int count = Math.max(projects.size(), notes.size());
        // Keep ordinary rows together; split only rows too tall for an entire page.
        float rowHeight = count * 12 + 12;
        if (rowHeight <= 572 && y - rowHeight < BOTTOM) newPage();
        int offset = 0;
        while (offset < count) {
            if (y - BOTTOM < 28) newPage();
            int capacity = Math.max(1, (int) ((y - BOTTOM - 12) / 12));
            int lines = Math.min(count - offset, capacity);
            float height = lines * 12 + 12;
            fill(LEFT, y - height, WIDTH, height, rowIndex % 2 == 0 ? LIGHT : Color.WHITE);
            text(offset == 0 ? date : "(continued)", 52, y - 16, 9, false, NAVY);
            if (offset == 0) {
                float numberWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(printable(hours)) / 1000 * 9;
                text(hours, (projectColumn ? 305 : 210) - numberWidth, y - 16, 9, true, NAVY);
            }
            for (int i = 0; i < lines; i++) {
                int index = offset + i;
                float baseline = y - 16 - i * 12;
                if (projectColumn && index < projects.size()) text(projects.get(index), 133, baseline, 9, false, NAVY);
                if (index < notes.size()) text(notes.get(index), projectColumn ? 320 : 235, baseline, 9, false, MUTED);
            }
            y -= height;
            offset += lines;
        }
        rowIndex++;
    }

    void total(String hours) throws IOException {
        table = false;
        ensure(58);
        y -= 10;
        fill(LEFT, y - 34, WIDTH, 36, NAVY);
        text("TOTAL APPROVED HOURS", 56, y - 20, 10, true, Color.WHITE);
        float numberWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(printable(hours)) / 1000 * 16;
        text(hours, 554 - numberWidth, y - 21, 16, true, Color.WHITE);
        y -= 52;
    }

    void amountTotal(String amount) throws IOException {
        table = false;
        ensure(44);
        fill(LEFT, y - 28, WIDTH, 30, NAVY);
        text("TOTAL APPROVED AMOUNT", 56, y - 17, 10, true, Color.WHITE);
        right(amount, 554, y - 18, 14, true, Color.WHITE);
        y -= 42;
    }

    byte[] finish() throws IOException {
        content.close();
        content = null;
        int pages = document.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                content = footer;
                fill(LEFT, 49, WIDTH, 0.5f, new Color(204, 213, 219));
                text("MAXWELL NETWORK INC.  |  Confidential employee record", LEFT, 34, 8, false, MUTED);
                text("Page " + (i + 1) + " of " + pages, 505, 34, 8, false, MUTED);
            } finally {
                content = null;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    private void ensure(float height) throws IOException {
        if (y - height < BOTTOM) newPage();
    }

    private void fill(float x, float bottom, float width, float height, Color color) throws IOException {
        content.setNonStrokingColor(color);
        content.addRect(x, bottom, width, height);
        content.fill();
    }

    private void text(String value, float x, float baseline, float size, boolean bold, Color color) throws IOException {
        content.beginText();
        content.setNonStrokingColor(color);
        content.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, size);
        content.newLineAtOffset(x, baseline);
        content.showText(printable(value));
        content.endText();
    }

    private void right(String value, float right, float baseline, float size, boolean bold, Color color) throws IOException {
        var font = bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
        float width = font.getStringWidth(printable(value)) / 1000 * size;
        text(value, right - width, baseline, size, bold, color);
    }

    private static List<String> wrap(String value, float width, float size, boolean bold) throws IOException {
        var font = bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
        List<String> lines = new ArrayList<>();
        String remaining = printable(value).trim();
        while (!remaining.isEmpty()) {
            int end = 0;
            while (end < remaining.length() && font.getStringWidth(remaining.substring(0, end + 1)) / 1000 * size <= width) end++;
            end = Math.max(1, end);
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) end = space;
            }
            lines.add(remaining.substring(0, end));
            remaining = remaining.substring(end).trim();
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    // Built-in PDF fonts use WinAnsi. Preserve supported accents, replace unsupported glyphs explicitly.
    private static String printable(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(code -> {
            if (Character.isWhitespace(code) || Character.isISOControl(code)) {
                result.append(' ');
            } else {
                String glyph = new String(Character.toChars(code));
                try {
                    PDType1Font.HELVETICA.encode(glyph);
                    result.append(glyph);
                } catch (IOException | IllegalArgumentException ex) {
                    result.append('?');
                }
            }
        });
        return result.toString();
    }

    @Override public void close() throws IOException {
        try {
            if (content != null) content.close();
        } finally {
            document.close();
        }
    }
}
