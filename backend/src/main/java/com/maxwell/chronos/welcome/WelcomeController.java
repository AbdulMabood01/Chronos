package com.maxwell.chronos.welcome;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class WelcomeController {

    @GetMapping("/welcome")
    public WelcomeResponse welcome() {
        return new WelcomeResponse("Welcome to Chronos - Maxwell Network Employee Timekeeping & Vacation Management System");
    }
}
