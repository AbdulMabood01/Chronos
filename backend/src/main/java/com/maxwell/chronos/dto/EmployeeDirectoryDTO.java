package com.maxwell.chronos.dto;
import com.maxwell.chronos.enums.UserRole;
public record EmployeeDirectoryDTO(Long id, String employeeId, String firstName, String lastName,
                                   String email, String jobTitle, UserRole role, Boolean isActive) {}
