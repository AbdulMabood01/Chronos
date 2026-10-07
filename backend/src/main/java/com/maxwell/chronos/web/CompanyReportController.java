package com.maxwell.chronos.web;
import com.maxwell.chronos.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.time.*;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@RestController @RequestMapping("/companies/{companyId}/reports") @RequiredArgsConstructor
public class CompanyReportController {
    private final ReportService reports;
    private final CompanyAccessService access;
    private final UserService users;
    private final JdbcTemplate db;
    private long actor(long company,Jwt jwt){long actor=users.findUserEntityByEmail(jwt.getClaimAsString("preferred_username")).getId();access.requireActiveCompanyAccess(company,actor);return actor;}
    private void period(long company,long period,long actor){
        var ids=db.queryForList("SELECT p.id FROM timesheet_approval_periods a JOIN projects p ON p.id=a.project_id WHERE a.id=? AND p.company_id=?",Long.class,period,company);
        if(ids.isEmpty()||!reports.canReadProjectReport(ids.get(0),actor))throw new AccessDeniedException("Company report access denied");
    }
    @GetMapping("/timesheet-periods") public List<Map<String,Object>> periods(@PathVariable long companyId,@RequestParam int year,@RequestParam int month,@AuthenticationPrincipal Jwt jwt){
        long actor=actor(companyId,jwt);return reports.approvalPeriods(companyId,year,month,actor);
    }
    @GetMapping("/timesheet-periods/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable long companyId,@PathVariable long id,@AuthenticationPrincipal Jwt jwt){long actor=actor(companyId,jwt);period(companyId,id,actor);return download(reports.exportApprovalPeriodPdf(id,actor),"application/pdf","timesheet-period-"+id+".pdf");}
    @GetMapping("/timesheet-periods/export") public ResponseEntity<byte[]> archive(@PathVariable long companyId,@RequestParam String ids,@AuthenticationPrincipal Jwt jwt){
        long actor=actor(companyId,jwt);var selected=Arrays.stream(ids.split(",")).map(String::trim).filter(s->!s.isEmpty()).map(Long::valueOf).distinct().toList();
        if(selected.isEmpty()||selected.size()>200)throw new IllegalArgumentException("Choose between 1 and 200 approved periods");for(long id:selected)period(companyId,id,actor);
        return download(reports.exportApprovalPeriods(selected,actor),"application/zip","company-"+companyId+"-timesheet-periods.zip");
    }
    @GetMapping("/vacation/export") public ResponseEntity<byte[]> leave(@PathVariable long companyId,@RequestParam int year,@AuthenticationPrincipal Jwt jwt)throws IOException{
        long actor=actor(companyId,jwt);access.requireCompanyCapability(companyId,actor,"canViewCompanyReports");if(year<1900||year>9998)throw new IllegalArgumentException("Choose a valid leave year");
        var rows=db.queryForList("SELECT concat_ws(' ',u.first_name,u.last_name) AS employee,m.employee_id,v.start_date,v.end_date,v.vacation_type,v.accounting_type,v.status,v.hours FROM vacation_requests v JOIN users u ON u.id=v.user_id JOIN company_memberships m ON m.company_id=v.company_id AND m.user_id=v.user_id WHERE v.company_id=? AND v.start_date<=? AND v.end_date>=? ORDER BY v.start_date,u.last_name",companyId,LocalDate.of(year,12,31),LocalDate.of(year,1,1));
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=workbook.createSheet("Company leave");sheet.createRow(0).createCell(0).setCellValue(access.companyDisplayName(companyId));String[] fields={"employee","employee_id","start_date","end_date","vacation_type","accounting_type","status","hours"};var header=sheet.createRow(1);for(int i=0;i<fields.length;i++)header.createCell(i).setCellValue(fields[i].replace('_',' '));int index=2;
            for(var row:rows){var excel=sheet.createRow(index++);for(int i=0;i<fields.length;i++)excel.createCell(i).setCellValue(Objects.toString(row.get(fields[i]),""));}for(int i=0;i<fields.length;i++)sheet.autoSizeColumn(i);workbook.write(output);return download(output.toByteArray(),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","company-"+companyId+"-leave-"+year+".xlsx");
        }
    }
    @GetMapping("/summary") public List<Map<String,Object>> summary(@PathVariable long companyId,@RequestParam int year,@RequestParam int month,@AuthenticationPrincipal Jwt jwt){
        long actor=actor(companyId,jwt);YearMonth selected=YearMonth.of(year,month);
        return db.queryForList("SELECT p.id AS project_id,p.code,p.name,COALESCE(sum(e.hours),0) AS total_hours FROM projects p LEFT JOIN time_entries e ON e.project_id=p.id AND e.entry_date BETWEEN ? AND ? WHERE p.company_id=? GROUP BY p.id ORDER BY p.code",selected.atDay(1),selected.atEndOfMonth(),companyId).stream().filter(row->reports.canReadProjectReport(((Number)row.get("project_id")).longValue(),actor)).toList();
    }
    private ResponseEntity<byte[]> download(byte[] data,String type,String filename){return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).body(data);}
}
