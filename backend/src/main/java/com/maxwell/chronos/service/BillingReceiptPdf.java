package com.maxwell.chronos.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** A billing document, never an approved employee letter or a tax invoice. */
final class BillingReceiptPdf {
    private BillingReceiptPdf() {}
    static byte[] render(String text){
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()){
            document.getDocumentInformation().setTitle("Chronos payment receipt");
            document.getDocumentInformation().setSubject("One-off company plan payment receipt");
            List<String> lines=new ArrayList<>();
            for(String raw:text.split("\\R")){
                String clean=raw.codePoints().collect(StringBuilder::new,(s,c)->s.append(c>=32&&c<=126?(char)c:'?'),StringBuilder::append).toString();
                StringBuilder line=new StringBuilder();for(String word:clean.split(" ")){
                    if(line.length()+word.length()>83){lines.add(line.toString());line.setLength(0);}
                    while(word.length()>83){lines.add(word.substring(0,83));word=word.substring(83);}
                    if(!line.isEmpty())line.append(' ');line.append(word);
                }lines.add(line.toString());
            }
            for(int offset=0;offset<lines.size();offset+=44){var page=new PDPage(PDRectangle.LETTER);document.addPage(page);try(var content=new PDPageContentStream(document,page)){
                content.beginText();content.setFont(PDType1Font.HELVETICA_BOLD,18);content.newLineAtOffset(50,735);content.showText("Chronos payment receipt");content.endText();
                content.beginText();content.setFont(PDType1Font.COURIER,10);content.setLeading(14);content.newLineAtOffset(50,705);
                for(int i=offset;i<Math.min(lines.size(),offset+44);i++){content.showText(lines.get(i));content.newLine();}content.endText();
            }}document.save(output);return output.toByteArray();
        }catch(java.io.IOException ex){throw new IllegalStateException("Receipt rendering failed",ex);}
    }
}
