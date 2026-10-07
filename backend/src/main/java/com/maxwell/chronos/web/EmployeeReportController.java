package com.maxwell.chronos.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Global company workflow endpoints have been replaced by company-scoped APIs. */
@RestController @RequestMapping("/employee-reports")
public class EmployeeReportController {
    @RequestMapping({"","/**"}) public void retired(){throw new ResponseStatusException(HttpStatus.GONE,"Use the selected company's workflow API");}
}
