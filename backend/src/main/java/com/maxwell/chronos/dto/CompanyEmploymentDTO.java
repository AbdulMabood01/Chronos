package com.maxwell.chronos.dto;

import java.time.LocalDate;

public record CompanyEmploymentDTO(long companyId, long userId, String employeeId, String jobTitle,
                                   LocalDate joiningDate, String membershipStatus, long version, boolean locked) {
    public CompanyEmploymentDTO(long companyId,long userId,String employeeId,String jobTitle,LocalDate joiningDate,String membershipStatus,long version) {
        this(companyId,userId,employeeId,jobTitle,joiningDate,membershipStatus,version,false);
    }
}
