package com.maxwell.chronos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxwell.chronos.domain.LetterRequest;
import com.maxwell.chronos.dto.LetterReviewRequest;
import com.maxwell.chronos.enums.LetterRequestType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Company-only configuration. Images are copied into each issued document, never linked to mutable URLs. */
@Service @RequiredArgsConstructor @Transactional
public class CompanyLetterTemplates {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final CompanyWorkflowAccess flow;
    public record Employer(String id,String name,String address,String email,String phone,String website,String identifiers,String logo) {}
    public record Signatory(String id,String name,String title,String signature) {}
    public record Template(String type,boolean enabled,String body,String employerId,String signatoryId,boolean signatureRequired) {}
    public record Configuration(long version,List<Employer> employers,List<Signatory> signatories,List<Template> templates) {}
    public record Definition(String name,String address,String email,String phone,String website,String identifiers,String logo,
                             String hrName,String hrTitle,String signature,String body,boolean signatureRequired) {
        public Definition {name=clean(name);address=clean(address);email=clean(email);phone=clean(phone);website=clean(website);identifiers=clean(identifiers);logo=clean(logo);hrName=clean(hrName);hrTitle=clean(hrTitle);signature=clean(signature);body=clean(body);}
        private static String clean(String value){return Objects.toString(value,"").strip();}
    }
    private static final Set<String> TOKENS=Set.of("employee_name","job_title","joining_date","issue_date","employer_name","destination_country","travel_start_date","travel_end_date","vacation_start_date","vacation_end_date");
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{\\{([^{}]+)}}");
    public Configuration defaults() {
        String intro="At the request of {{employee_name}}, this letter confirms their current employment with {{employer_name}} as {{job_title}}. Their employment began on {{joining_date}}.";
        return new Configuration(-1,List.of(),List.of(),List.of(
            new Template("EMPLOYMENT_VERIFICATION",false,"At the request of {{employee_name}}, this letter confirms their current employment with {{employer_name}}.\n\n{{employee_name}} has been employed since {{joining_date}} and currently holds the position of {{job_title}}. Our records show that their employment is active as of the date of this letter.\n\nThis verification reflects our employment records and is provided for the recipient's use. For further confirmation, please contact our Human Resources department using the details on this letter.","","",false),
            new Template("TRAVEL",false,intro+"\n\nAccording to the information provided with their request, {{employee_name}} plans to travel to {{destination_country}} from {{travel_start_date}} through {{travel_end_date}}.\n\n{{employee_name}} remains an active employee and is expected to resume their work responsibilities after the stated travel period. This letter confirms employment and the travel dates supplied to us; it does not replace any required travel authorization.","","",false),
            new Template("VACATION",false,intro+"\n\n{{employee_name}} has provided vacation dates of {{vacation_start_date}} through {{vacation_end_date}}. These dates are recorded with the company for administrative review and remain subject to the applicable leave approval process.\n\n{{employee_name}} is expected to resume their regular work responsibilities following any approved leave. Please contact our Human Resources department if further employment verification is needed.","","",false)));
    }
    /** One company and HR identity applies to every letter; wording is maintained centrally. */
    public Configuration shared(Configuration c) {
        if(c==null||c.employers()==null||c.signatories()==null)throw new IllegalArgumentException("Provide company and HR details.");
        Employer employer=c.employers().stream().findFirst().orElse(null);
        Signatory hr=c.signatories().stream().findFirst().orElse(null);
        boolean ready=employer!=null&&hr!=null&&!blank(employer.name())&&!blank(employer.address())&&!blank(employer.email())&&!blank(hr.name())&&!blank(hr.title());
        var letters=defaults().templates().stream().map(t->new Template(t.type(),ready,t.body(),employer==null?"":employer.id(),hr==null?"":hr.id(),false)).toList();
        return new Configuration(c.version(),employer==null?List.of():List.of(employer),hr==null?List.of():List.of(hr),letters);
    }
    private boolean blank(String s){return s==null||s.isBlank();}

    public Configuration read(long company) {
        var rows=db.queryForList("SELECT version,configuration::text FROM company_letter_settings WHERE company_id=?",company);
        if(rows.isEmpty())return defaults();
        try {var c=json.readValue((String)rows.getFirst().get("configuration"),Configuration.class);return shared(new Configuration(((Number)rows.getFirst().get("version")).longValue(),c.employers(),c.signatories(),c.templates()));}
        catch(Exception e){throw new IllegalStateException("Unable to read letter configuration",e);}
    }
    public Configuration adminRead(long company,String email){flow.admin(company,email);return read(company);}
    public Configuration save(long company,String email,Configuration c) {
        var actor=flow.lockMember(company,email,true);c=shared(c);validate(c);
        int changed;
        if(c.version()==-1)changed=db.update("INSERT INTO company_letter_settings(company_id,configuration,updated_by) VALUES (?,?::jsonb,?) ON CONFLICT DO NOTHING",company,serialize(c),actor.getId());
        else changed=db.update("UPDATE company_letter_settings SET configuration=?::jsonb,version=version+1,updated_by=?,updated_at=now() WHERE company_id=? AND version=?",serialize(c),actor.getId(),company,c.version());
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Letter settings changed. Reload before saving.");
        flow.activity(company,actor.getId(),"LETTER_SETTINGS_CHANGED","Company",company);return read(company);
    }
    public Map<String,Object> availability(long company,String email) {
        flow.member(company,email);var c=read(company);var available=new ArrayList<Map<String,Object>>();
        for(var t:c.templates())if(t.enabled()){var d=definition(c,t,null);available.add(Map.of("type",t.type(),"definition",unsigned(d)));}
        return Map.of("companyId",company,"version",c.version(),"templates",available);
    }
    public Definition unsigned(Definition d){return new Definition(d.name(),d.address(),d.email(),d.phone(),d.website(),d.identifiers(),d.logo(),d.hrName(),d.hrTitle(),"",d.body(),d.signatureRequired());}
    public Definition resolve(long company,LetterRequestType type,LetterReviewRequest review) {
        if(review!=null&&review.configurationVersion()==null)throw new IllegalArgumentException("Reload letter settings before previewing or approving.");
        var c=read(company);if(review!=null&&review.configurationVersion()!=null&&review.configurationVersion()!=c.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Letter settings changed. Reload the letter before approval.");
        var t=c.templates().stream().filter(x->x.type().equals(type.name())&&x.enabled()).findFirst().orElseThrow(()->new IllegalArgumentException("Complete the company and HR details before requesting a letter."));
        var base=definition(c,t,null);var resolved=review!=null&&review.letter()!=null?review.letter():base;
        validateDefinition(resolved,true);
        if(base.signatureRequired()!=resolved.signatureRequired())throw new IllegalArgumentException("The template signature requirement cannot be overridden on a letter.");
        if(!base.equals(resolved)&&(review.reviewNote()==null||review.reviewNote().isBlank()))throw new IllegalArgumentException("Explain changes to the letter template, employer, or signatory before approval.");
        return resolved;
    }
    public Definition definition(Configuration c,Template t,Definition override) {
        var e=c.employers().stream().filter(x->x.id().equals(t.employerId())).findFirst().orElseThrow(()->new IllegalArgumentException("Select an employer for the template."));
        var h=c.signatories().stream().filter(x->x.id().equals(t.signatoryId())).findFirst().orElseThrow(()->new IllegalArgumentException("Select an HR signatory for the template."));
        return new Definition(e.name(),e.address(),e.email(),e.phone(),e.website(),e.identifiers(),e.logo(),h.name(),h.title(),h.signature(),t.body(),t.signatureRequired());
    }
    public void validate(Configuration c) {
        if(c==null||c.employers()==null||c.signatories()==null||c.templates()==null||c.employers().size()>20||c.signatories().size()>20||c.templates().size()!=3)throw new IllegalArgumentException("Provide three letter templates and at most 20 employers and signatories.");
        var ids=new HashSet<String>();for(var e:c.employers()){if(e==null||!ids.add(required(e.id(),80)))throw new IllegalArgumentException("Employer IDs must be unique.");text(e.name(),200);text(e.address(),1000);text(e.email(),255);text(e.phone(),80);text(e.website(),255);text(e.identifiers(),1000);image(e.logo());}
        ids.clear();for(var h:c.signatories()){if(h==null||!ids.add(required(h.id(),80)))throw new IllegalArgumentException("Signatory IDs must be unique.");text(h.name(),200);text(h.title(),120);image(h.signature());}
        ids.clear();for(var t:c.templates()){if(t==null||!ids.add(t.type()))throw new IllegalArgumentException("Letter types must be unique.");try{LetterRequestType.valueOf(t.type());}catch(Exception e){throw new IllegalArgumentException("Unsupported letter type.");}body(t.body());if(t.enabled())validateDefinition(definition(c,t,null),true);}
        if(serialize(c).length()>12_000_000)throw new IllegalArgumentException("Letter settings are too large.");
    }
    public void validateDefinition(Definition d,boolean complete) {
        if(d==null)throw new IllegalArgumentException("Letter details are required.");
        text(d.name(),200);text(d.address(),1000);text(d.email(),255);text(d.phone(),80);text(d.website(),255);text(d.identifiers(),1000);text(d.hrName(),200);text(d.hrTitle(),120);body(d.body());image(d.logo());image(d.signature());
        if(complete){required(d.name(),200);required(d.address(),1000);required(d.hrName(),200);required(d.hrTitle(),120);required(d.body(),10000);if(!required(d.email(),255).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Enter a valid HR contact email.");if(d.signatureRequired())required(d.signature(),1_400_000);}
    }
    private static String required(String value,int max){text(value,max);if(value==null||value.isBlank())throw new IllegalArgumentException("Complete the employer name, address, HR email, signatory name/title, wording, and any required signature.");return value;}
    private static void text(String value,int max){if(value!=null&&(value.length()>max||value.indexOf('\0')>=0))throw new IllegalArgumentException("Letter field is too long or contains invalid characters.");}
    private static void body(String value){text(value,10000);if(value==null)return;var m=PLACEHOLDER.matcher(value);while(m.find())if(!TOKENS.contains(m.group(1)))throw new IllegalArgumentException("Unsupported placeholder: "+m.group(1));if(PLACEHOLDER.matcher(value).replaceAll("").contains("{{")||PLACEHOLDER.matcher(value).replaceAll("").contains("}}"))throw new IllegalArgumentException("Invalid template placeholder.");}
    public static byte[] image(String value) {
        if(value==null||value.isBlank())return null;
        if(value.length()>1_400_000||!value.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+"))throw new IllegalArgumentException("Upload a PNG or JPEG image up to 1 MB.");
        try {byte[] bytes=Base64.getDecoder().decode(value.substring(value.indexOf(',')+1));if(bytes.length>1_048_576)throw new IllegalArgumentException();
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException();ImageReader reader=readers.next();try{reader.setInput(stream);if(reader.getWidth(0)>3000||reader.getHeight(0)>3000||!Set.of("png","jpeg","jpg").contains(reader.getFormatName().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException();}finally{reader.dispose();}}
            return bytes;
        }catch(Exception e){throw new IllegalArgumentException("Upload a valid PNG or JPEG up to 1 MB and 3000 pixels per side.");}
    }
    public String render(Definition d,LetterRequest r,LetterReviewRequest review,LocalDate date) {
        var values=new LinkedHashMap<String,String>();values.put("employee_name",review==null?r.getRequestedFullName():review.fullName());values.put("job_title",review==null?r.getRequestedJobTitle():review.jobTitle());values.put("joining_date",dateText(review==null?r.getEmploymentStartDate():review.employmentStartDate()));values.put("issue_date",dateText(date));values.put("employer_name",d.name());values.put("destination_country",Objects.toString(r.getDestinationCountry(),"the requested destination"));values.put("travel_start_date",dateText(r.getTravelStartDate()));values.put("travel_end_date",dateText(r.getTravelEndDate()));values.put("vacation_start_date",dateText(r.getVacationStartDate()));values.put("vacation_end_date",dateText(r.getVacationEndDate()));
        var matcher=PLACEHOLDER.matcher(d.body());var out=new StringBuffer();while(matcher.find())matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(Objects.toString(values.get(matcher.group(1)),"")));matcher.appendTail(out);
        return d.name()+"\n"+dateText(date)+"\n\nTo whom it may concern,\n\nSubject: "+displayType(r.getRequestType())+"\n\n"+out+"\n\nBest Regards,\n"+d.hrName()+"\n"+d.hrTitle()+"\n\nEmployer Information\n"+d.name()+"\n"+d.address()+"\n"+d.email()+optional(d.phone())+optional(d.website())+optional(d.identifiers());
    }
    private String dateText(LocalDate date){return date==null?"the requested date":date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy",Locale.US));}
    private String displayType(LetterRequestType type){return switch(type){case EMPLOYMENT_VERIFICATION->"Employment Verification Letter";case TRAVEL->"Travel Letter";case VACATION->"Vacation Letter";};}
    private String optional(String s){return s==null||s.isBlank()?"":"\n"+s;}
    public String serialize(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid letter configuration",e);}}
    public Definition parse(String value){try{return json.readValue(value,Definition.class);}catch(Exception e){throw new IllegalStateException("Invalid issued letter",e);}}
    public byte[] previewTemplate(Definition d,LetterRequest r){return CompanyLetterPdf.render(d.name(),r.getRequestType().name().replace('_',' '),"SAMPLE",false,render(d,r,null,LocalDate.now()),d);}
}
