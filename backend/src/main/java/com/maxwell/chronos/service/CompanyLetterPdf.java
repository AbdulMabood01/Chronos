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
final class CompanyLetterPdf implements AutoCloseable {
    private static final Color NAVY = new Color(16, 51, 71), WINE = new Color(130, 6, 56), MUTED = new Color(92, 109, 121);
    private static final float LEFT = 54, WIDTH = 504, BOTTOM = 90;
    private final PDDocument document = new PDDocument();
    private final String reference;
    private final boolean approved;
    private final String company;
    private CompanyLetterTemplates.Definition branding;

    private PDPageContentStream content;
    private float y;

    private CompanyLetterPdf(String company,String reference,boolean approved){this.company=company;this.reference=reference;this.approved=approved;}
    static byte[] render(String company,String title, String reference, boolean approved, String letter) {
        return render(company,title,reference,approved,letter,null);
    }
    static byte[] render(String company,String title,String reference,boolean approved,String letter,CompanyLetterTemplates.Definition branding) {
        try (CompanyLetterPdf pdf = new CompanyLetterPdf(company,reference, approved)) {
            pdf.branding=branding;
            pdf.document.getDocumentInformation().setTitle(title + " | "+company);
            pdf.document.getDocumentInformation().setAuthor(company);
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
                    pdf.ensure(160);
                    pdf.text("Best regards,", LEFT, pdf.y, 10.5f, PDType1Font.TIMES_ROMAN, NAVY);
                    pdf.y -= 18;
                    if(branding!=null&&branding.signature()!=null&&!branding.signature().isBlank()) {
                        pdf.drawImage(branding.signature(),LEFT,pdf.y-45,140,40);
                        pdf.y-=52;
                    }
                    String[] signature = section.split("\\R");
                    for (int n = 1; n < signature.length; n++) {
                        for(String line:wrap(signature[n],n==1?PDType1Font.HELVETICA_BOLD:PDType1Font.HELVETICA,n==1?11:9,WIDTH)) {
                            pdf.ensure(15);pdf.text(line,LEFT,pdf.y,n==1?11:9,n==1?PDType1Font.HELVETICA_BOLD:PDType1Font.HELVETICA,n==1?NAVY:MUTED);pdf.y-=15;
                        }
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
        if(branding!=null&&branding.logo()!=null&&!branding.logo().isBlank())drawImage(branding.logo(),LEFT,710,150,55);
        if(branding==null){
            right(company,558,748,11,PDType1Font.HELVETICA_BOLD,NAVY);
            right("Human Resources",558,729,9,PDType1Font.HELVETICA,MUTED);
            right("Company administration",558,712,8,PDType1Font.HELVETICA,MUTED);
        }else{
            float headerY=748;
            for(String line:wrap(company,PDType1Font.HELVETICA_BOLD,9,260)){right(line,558,headerY,9,PDType1Font.HELVETICA_BOLD,NAVY);headerY-=11;}
            right("Human Resources",558,headerY-5,8,PDType1Font.HELVETICA,MUTED);
        }
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
        List<String> labels = new ArrayList<>();
        if (branding != null) {
            rows = new String[]{"Employer Information", "Company: " + branding.name(),
                "Address: " + branding.address(), "Email: " + branding.email(),
                branding.phone() == null || branding.phone().isBlank() ? "" : "Telephone: " + branding.phone(),
                branding.website() == null || branding.website().isBlank() ? "" : "Website: " + branding.website(),
                Objects.toString(branding.identifiers(), "")};
        }
        for (int i = 1; i < rows.length; i++) {
            if (rows[i].isBlank()) continue;
            String label=i==1?"Company":i==2?"Address":rows[i].contains("@")?"Email":rows[i].matches("(?i)^(https?://|www\\.).*")?"Website":"Contact details";
            for (String row : rows[i].split("\\R")) {
                int separator = row.indexOf(':');
                boolean labeled = separator > 0 && !row.matches("(?i)^https?://.*");
                String rowLabel = labeled ? row.substring(0, separator).strip() : label;
                String value = labeled ? row.substring(separator + 1).strip() : row;
                var lines=wrap(value,PDType1Font.HELVETICA,8,WIDTH-180);
                var headings=wrap(rowLabel,PDType1Font.HELVETICA_BOLD,8,140);
                for(int n=0;n<Math.max(lines.size(),headings.size());n++){
                    details.add(n<lines.size()?lines.get(n):"");
                    labels.add(n<headings.size()?headings.get(n):"");
                }
                label = "";
            }
        }
        float height = 32 + details.size() * 11;
        ensure(height + 8);
        fill(LEFT, y - height, WIDTH, height, new Color(245, 247, 249));
        fill(LEFT, y - height, 2, height, WINE);
        text("EMPLOYER INFORMATION", LEFT + 13, y - 17, 8, PDType1Font.HELVETICA_BOLD, NAVY);
        y -= 33;
        for (int i=0;i<details.size();i++) {
            text(labels.get(i), LEFT + 13, y, 8, PDType1Font.HELVETICA_BOLD, NAVY);
            text(details.get(i), LEFT + 165, y, 8, PDType1Font.HELVETICA, MUTED);
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
                float footerY=40;
                if(branding==null)text(company,LEFT,52,8,PDType1Font.HELVETICA_BOLD,WINE);
                else {footerY=52;for(String line:wrap(company,PDType1Font.HELVETICA_BOLD,8,350)){text(line,LEFT,footerY,8,PDType1Font.HELVETICA_BOLD,WINE);footerY-=9;}}
                right("Page " + (i + 1) + " of " + document.getNumberOfPages(), 558, 52, 8, PDType1Font.HELVETICA, MUTED);
                text("Employee letter  |  "+reference, LEFT, Math.min(37,footerY-3), 8, PDType1Font.HELVETICA, MUTED);
            } finally { content = null; }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); document.save(out); return out.toByteArray();
    }
    private void drawImage(String data,float x,float bottom,float maxWidth,float maxHeight) throws IOException {
        var image=PDImageXObject.createFromByteArray(document,CompanyLetterTemplates.image(data),"Company letter asset");
        float scale=Math.min(maxWidth/image.getWidth(),maxHeight/image.getHeight());
        content.drawImage(image,x,bottom,image.getWidth()*scale,image.getHeight()*scale);
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
