package com.maxwell.chronos.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
public class VacationController {
    @RequestMapping(value={"/vacation","/vacation/{id}","/vacation/{id}/submit","/vacation/team-calendar","/vacation/pending","/vacation/my","/approvals/vacation/{id}/approve","/approvals/vacation/{id}/reject"},method={RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
    public void retired(){throw new ResponseStatusException(HttpStatus.GONE,"Use the selected company's leave workflow");}
}
