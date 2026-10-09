package com.maxwell.chronos.dto;

import java.time.LocalDate;

public record LetterReviewRequest(String fullName, String jobTitle, LocalDate employmentStartDate,
                                  String reviewNote, Long configurationVersion,
                                  com.maxwell.chronos.service.CompanyLetterTemplates.Definition letter) {
    public LetterReviewRequest(String name,String title,LocalDate date,String note){this(name,title,date,note,null,null);}
}
