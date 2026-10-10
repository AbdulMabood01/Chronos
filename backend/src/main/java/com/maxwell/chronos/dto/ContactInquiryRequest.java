package com.maxwell.chronos.dto;

import jakarta.validation.constraints.*;

public record ContactInquiryRequest(
    @NotBlank(message = "Enter your name") @Size(max = 100) String name,
    @NotBlank(message = "Enter your company") @Size(max = 150) String company,
    @NotBlank(message = "Enter your email") @Email(message = "Enter a valid email")
    @Size(max = 254) @Pattern(regexp = "[^\r\n]*") String email,
    @Size(max = 40) @Pattern(regexp = "[+0-9(). xX#-]*", message = "Enter a valid phone number") String phone,
    @NotBlank(message = "Enter a short message") @Size(max = 3000) String message,
    @NotNull @Pattern(regexp = "LANDING|LOGIN") String source,
    @Size(max = 200) String website
) {}
