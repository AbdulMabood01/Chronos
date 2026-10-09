package com.maxwell.chronos.service;
import com.maxwell.chronos.dto.*;
import com.maxwell.chronos.enums.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="chronos.localDbTest",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://localhost:5432/chronos_dev","spring.datasource.username=chronos_user","spring.datasource.password=chronos_password","chronos.jwtSigningKey=Q2hyb25vcy1kZXZlbG9wbWVudC1rZXktMzItYnl0ZXMtbWluaW11bQ==","chronos.email-alerts.enabled=false"})
@Transactional
class CompanyLettersLocalDbTest {
 @Autowired JdbcTemplate db;
 @Autowired CompanyLetterRequestService letters;
 @Autowired CompanyMembershipService memberships;
 @Autowired CompanyLeaveService leave;
 @Autowired CompanyLetterTemplates templates;
 @MockitoBean NotificationService notifications;
 @MockitoBean EmailAlertService emails;
 long a,b,admin,member;
 long company(){return db.queryForObject("INSERT INTO companies(name,slug) VALUES ('Letters Company',?) RETURNING id",Long.class,"letters-"+UUID.randomUUID());}
 long user(){String key="letters-"+UUID.randomUUID();return db.queryForObject("INSERT INTO users(employee_id,email,first_name,last_name,password_hash,entra_id) VALUES (?,?,'Letter','Member','hash',?) RETURNING id",Long.class,key,key+"@example.com",UUID.randomUUID().toString());}
 @BeforeEach void setup(){a=company();b=company();admin=user();member=user();for(long c:new long[]{a,b})for(long u:new long[]{admin,member})db.update("INSERT INTO company_memberships(company_id,user_id,status,employee_id,job_title,joining_date) VALUES (?,?,'ACTIVE',?,'Company title','2025-01-01')",c,u,"EMP-"+u);db.update("INSERT INTO role_assignments(company_id,user_id,role_key) VALUES (?,?,'COMPANY_ADMIN')",a,admin);db.update("UPDATE company_settings SET company_name='Company A Letters' WHERE company_id=?",a);db.update("UPDATE company_settings SET company_name='Company B Letters' WHERE company_id=?",b);configureLetters();}
 CreateLetterRequest input(){CreateLetterRequest in=new CreateLetterRequest();in.setRequestType(LetterRequestType.EMPLOYMENT_VERIFICATION);in.setRequestedFullName("Letter Member");in.setRequestedJobTitle("Confirmed title");in.setEmploymentStartDate(LocalDate.of(2025,1,1));return in;}
 void configureLetters(){for(long company:new long[]{a,b}){
  var defaults=templates.defaults();
  var employer=new CompanyLetterTemplates.Employer("employer",company==a?"Company A Letters":"Company B Letters","100 Business Street","hr@example.com","","","","");
  var hr=new CompanyLetterTemplates.Signatory("hr","Casey HR","HR Director","");
  var enabled=defaults.templates().stream().map(t->new CompanyLetterTemplates.Template(t.type(),true,t.body(),"employer","hr",false)).toList();
  db.update("INSERT INTO company_letter_settings(company_id,configuration) VALUES (?,?::jsonb)",company,templates.serialize(new CompanyLetterTemplates.Configuration(0,java.util.List.of(employer),java.util.List.of(hr),enabled)));
 }}
 LetterReviewRequest review(){return new LetterReviewRequest("Letter Member","Confirmed title",LocalDate.of(2025,1,1),null,templates.read(a).version(),null);}
 @Test void lettersAreOwnedByCompanyAndUseMembershipEmploymentAndBranding() throws Exception {
  var first=letters.createLetterRequest(a,member,input());letters.createLetterRequest(b,member,input());assertEquals(a,first.getCompanyId());assertEquals(0,first.getVersion());assertEquals("Company title",first.getJobTitle());assertEquals("2",first.getEmployeeId());assertEquals(1,letters.getMyRequests(a,member).size());assertEquals(1,letters.getPendingRequests(a,admin).size());
  assertThrows(AccessDeniedException.class,()->letters.getPendingRequests(b,admin));assertEquals(404,assertThrows(ResponseStatusException.class,()->letters.getRequest(b,first.getId(),member,true)).getStatusCode().value());
  try(var draft=org.apache.pdfbox.pdmodel.PDDocument.load(letters.generatePdf(a,first.getId(),member,true))){assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(draft).contains("PREVIEW - NOT APPROVED"));}var approved=letters.approveLetterRequest(a,first.getId(),admin,review(),0);assertEquals(1,approved.getVersion());assertEquals(VacationStatus.APPROVED,approved.getStatus());
  try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(letters.generatePdf(a,first.getId(),member,false))){String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(text.contains("Company A Letters"));assertFalse(text.contains("Company B Letters"));assertFalse(text.contains("Maxwell"));assertFalse(text.contains("933751606"));}
 }
 @Test void ownRequestsAndStaleLetterDecisionsCannotBeApprovedOrRejected() {
  var own=letters.createLetterRequest(a,admin,input());assertThrows(IllegalArgumentException.class,()->letters.approveLetterRequest(a,own.getId(),admin,review(),0));assertThrows(IllegalArgumentException.class,()->letters.rejectLetterRequest(a,own.getId(),"No",admin,0));
  var request=letters.createLetterRequest(a,member,input());assertEquals(409,assertThrows(ResponseStatusException.class,()->letters.approveLetterRequest(a,request.getId(),admin,review(),7)).getStatusCode().value());assertEquals(VacationStatus.SUBMITTED,letters.getRequest(a,request.getId(),member,false).getStatus());
  assertThrows(IllegalArgumentException.class,()->letters.approveLetterRequest(a,request.getId(),admin,new LetterReviewRequest("Letter Member","Changed title",LocalDate.of(2025,1,1),null),0));
  var rejected=letters.rejectLetterRequest(a,request.getId(),"Employment details need review",admin,0);assertEquals(1,rejected.getVersion());assertEquals(VacationStatus.REJECTED,rejected.getStatus());
 }
 @Test void pendingLettersBlockRemovalAndRemovedOrPlatformActorsHaveNoLetterAccess() {
  var request=letters.createLetterRequest(a,member,input());assertEquals(409,assertThrows(ResponseStatusException.class,()->memberships.changeStatus(a,member,admin,new CompanyMembershipService.StatusInput("REMOVED",0L))).getStatusCode().value());
  letters.rejectLetterRequest(a,request.getId(),"Reviewed before removal",admin,0);memberships.changeStatus(a,member,admin,new CompanyMembershipService.StatusInput("REMOVED",0L));assertThrows(AccessDeniedException.class,()->letters.getMyRequests(a,member));assertTrue(letters.getMyRequests(b,member).isEmpty());
  db.update("INSERT INTO role_assignments(user_id,role_key) VALUES (?,'PLATFORM_ADMIN')",member);assertThrows(AccessDeniedException.class,()->letters.getMyRequests(b,member));
 }
 @Test void deletingLeaveDraftPreservesItsCompanyAuditOwnership() {
  var input=new CompanyLeaveService.RequestInput(LocalDate.of(2026,10,6),LocalDate.of(2026,10,6),VacationType.VACATION,null,null,null);
  long id=((Number)leave.save(a,null,member,input).get("id")).longValue();leave.delete(a,id,member,0);
  assertEquals(2,db.queryForObject("SELECT count(*) FROM audit_logs WHERE company_id=? AND entity_type='VacationRequest' AND entity_id=?",Integer.class,a,id));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM vacation_requests WHERE id=?",Integer.class,id));
 }
 @Test void setupAndTemplateRevisionAreRequiredAndIssuedPdfsNeverChange() {
  db.update("DELETE FROM company_letter_settings WHERE company_id=?",b);
  assertThrows(IllegalArgumentException.class,()->letters.createLetterRequest(b,member,input()));
  assertEquals(java.util.List.of(),templates.availability(b,db.queryForObject("SELECT email FROM users WHERE id=?",String.class,member)).get("templates"));
  var request=letters.createLetterRequest(a,member,input());
  assertThrows(IllegalArgumentException.class,()->letters.approveLetterRequest(a,request.getId(),admin,new LetterReviewRequest("Letter Member","Confirmed title",LocalDate.of(2025,1,1),null),0));
  var approved=letters.approveLetterRequest(a,request.getId(),admin,review(),0);
  byte[] before=letters.generatePdf(a,request.getId(),member,false);
  db.update("UPDATE company_letter_settings SET version=version+1,configuration=jsonb_set(configuration,'{employers,0,name}','\"Changed employer\"'::jsonb) WHERE company_id=?",a);
  db.update("UPDATE company_settings SET company_name='Changed company' WHERE company_id=?",a);
  assertArrayEquals(before,letters.generatePdf(a,request.getId(),member,false));
  assertEquals(approved.getLetterPreview(),letters.getRequest(a,request.getId(),member,false).getLetterPreview());
  var next=letters.createLetterRequest(a,member,input());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->letters.approveLetterRequest(a,next.getId(),admin,new LetterReviewRequest("Letter Member","Confirmed title",LocalDate.of(2025,1,1),null,0L,null),0)).getStatusCode().value());
 }
 @Test void configurationIsCompanyAdminOnlyAndSignaturesAreHiddenFromEmployeeDrafts() throws Exception {
  String adminEmail=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,admin),memberEmail=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,member);
  assertThrows(AccessDeniedException.class,()->templates.adminRead(a,memberEmail));
  assertThrows(AccessDeniedException.class,()->templates.adminRead(b,adminEmail));
  var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);
  String asset="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(out.toByteArray());
  var current=templates.read(a);var employer=current.employers().getFirst();
  var config=new CompanyLetterTemplates.Configuration(current.version(),current.employers(),java.util.List.of(new CompanyLetterTemplates.Signatory("hr","Casey HR","HR Director",asset)),current.templates().stream().map(t->new CompanyLetterTemplates.Template(t.type(),true,t.body(),t.employerId(),t.signatoryId(),true)).toList());
  templates.save(a,adminEmail,config);
  assertFalse(templates.serialize(templates.availability(a,memberEmail)).contains(asset));
  var r=letters.createLetterRequest(a,member,input());
  try(var draft=org.apache.pdfbox.pdmodel.PDDocument.load(letters.generatePdf(a,r.getId(),member,false))){assertFalse(draft.getPage(0).getResources().getXObjectNames().iterator().hasNext());}
  letters.approveLetterRequest(a,r.getId(),admin,review(),0);
  try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(letters.generatePdf(a,r.getId(),member,false))){assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());}
  assertThrows(ResponseStatusException.class,()->templates.save(a,adminEmail,config));
 }
 @Test void overridesRequireAnExplanationAndInvalidTemplatesCannotBeEnabled() {
  var c=templates.read(a);var base=templates.definition(c,c.templates().getFirst(),null);
  var changed=new CompanyLetterTemplates.Definition("Separate employer",base.address(),base.email(),base.phone(),base.website(),base.identifiers(),base.logo(),"Other HR",base.hrTitle(),base.signature(),base.body(),false);
  var r=letters.createLetterRequest(a,member,input());
  assertThrows(IllegalArgumentException.class,()->letters.previewReview(a,r.getId(),admin,new LetterReviewRequest("Letter Member","Confirmed title",LocalDate.of(2025,1,1),null,c.version(),changed),0));
  var approved=letters.approveLetterRequest(a,r.getId(),admin,new LetterReviewRequest("Letter Member","Confirmed title",LocalDate.of(2025,1,1),"Issued by subsidiary HR",c.version(),changed),0);
  assertTrue(approved.getLetterPreview().contains("Separate employer"));assertTrue(approved.getLetterPreview().contains("Other HR"));
  var invalid=new CompanyLetterTemplates.Definition("Employer","Address","hr@example.com","","","","","HR","Director","","{{unsupported}}",false);
  assertThrows(IllegalArgumentException.class,()->templates.validateDefinition(invalid,true));
  assertThrows(IllegalArgumentException.class,()->CompanyLetterTemplates.image("data:image/svg+xml;base64,PHN2Zz4="));
 }
}
