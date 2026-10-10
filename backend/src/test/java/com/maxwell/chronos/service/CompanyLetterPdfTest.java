package com.maxwell.chronos.service;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CompanyLetterPdfTest {
 @Test void brandedLettersWrapLongNamesAndPreserveEveryParagraphAcrossPages() throws Exception {
  var bitmap=new BufferedImage(220,55,BufferedImage.TYPE_INT_RGB);var g=bitmap.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,220,55);g.setColor(java.awt.Color.BLUE);g.drawString("EXAMPLE COMPANY",20,30);g.dispose();
  var out=new ByteArrayOutputStream();ImageIO.write(bitmap,"png",out);String asset="data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
  var d=new CompanyLetterTemplates.Definition("Example Company With A Long Legal Employer Name For Reliable Letter Layout", "100 Business Street\nSuite 200, Chicago, IL","hr@example.com","555-0100","example.com","Registration: TEST-01",asset,"Casey Human Resources","Director of Human Resources",asset,"",true);
  for(boolean longLetter:new boolean[]{false,true}){
   String body="This verifies Sample Employee works as an Engineer. Their employment began on January 1, 2025.\n\n";
   if(longLetter)body+=("A detailed verification paragraph describing employment responsibilities and business requirements without omitting content. ".repeat(8)+"\n\n").repeat(12);
   String text=d.name()+"\n2026-10-06\n\nSubject: Employment verification\n\n"+body+"FINAL CONTENT MARKER\n\nBest Regards,\n"+d.hrName()+"\n"+d.hrTitle()+"\n\nEmployer Information\n"+d.name()+"\n"+d.address()+"\n"+d.email();
   byte[] bytes=CompanyLetterPdf.render(d.name(),"Employment Verification Letter","LTR-TEST",true,text,d);
   Path folder=Path.of("../reports/letter-pdf-qa");Files.createDirectories(folder);String name=longLetter?"long":"standard";Files.write(folder.resolve(name+".pdf"),bytes);
   try(var pdf=PDDocument.load(bytes)){String extracted=new PDFTextStripper().getText(pdf);assertTrue(extracted.contains("FINAL CONTENT MARKER"));assertTrue(extracted.contains("Casey Human Resources"));assertTrue(extracted.contains("hr@example.com"));assertTrue(extracted.contains("Telephone"));assertTrue(extracted.contains("555-0100"));assertTrue(extracted.contains("Website"));assertTrue(extracted.contains("Registration"));assertTrue(extracted.contains("TEST-01"));assertFalse(extracted.contains("PREVIEW - NOT APPROVED"));if(longLetter)assertTrue(pdf.getNumberOfPages()>1);var renderer=new PDFRenderer(pdf);for(int i=0;i<pdf.getNumberOfPages();i++)ImageIO.write(renderer.renderImageWithDPI(i,100),"png",folder.resolve(name+"-"+(i+1)+".png").toFile());}
  }
 }
}
