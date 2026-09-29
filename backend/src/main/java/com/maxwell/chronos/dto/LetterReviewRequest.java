package com.maxwell.chronos.dto;

import java.time.LocalDate;

public record LetterReviewRequest(String fullName, String jobTitle, LocalDate employmentStartDate,
                                  String reviewNote) {}
