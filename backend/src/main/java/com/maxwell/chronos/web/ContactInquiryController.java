package com.maxwell.chronos.web;

import com.maxwell.chronos.dto.ContactInquiryRequest;
import com.maxwell.chronos.service.ContactInquiryService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/contact")
public class ContactInquiryController {
    private final ContactInquiryService inquiries;
    public ContactInquiryController(ContactInquiryService inquiries) { this.inquiries = inquiries; }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("available", inquiries.available()));
    }

    @PostMapping("/inquiries")
    public Map<String, String> send(@Valid @RequestBody ContactInquiryRequest inquiry) {
        inquiries.send(inquiry);
        return Map.of("message", "Your inquiry has been sent. We will contact you to discuss the next steps.");
    }
}
