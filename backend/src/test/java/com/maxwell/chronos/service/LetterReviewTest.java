package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.LetterRequest;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.dto.LetterReviewRequest;
import com.maxwell.chronos.enums.LetterRequestType;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.enums.VacationStatus;
import com.maxwell.chronos.repository.LetterRequestRepository;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LetterReviewTest {
    @Test void approvedLetterUsesReviewedFactsAndCannotBeSelfApproved() {
        var requests = mock(LetterRequestRepository.class);
        var users = mock(UserRepository.class);
        var service = new LetterRequestService(requests, users, mock(AuditService.class), mock(NotificationService.class));
        var employee = User.builder().id(1L).firstName("Sam").lastName("Lee").role(UserRole.EMPLOYEE).build();
        var admin = User.builder().id(2L).role(UserRole.ADMIN).build();
        var letter = LetterRequest.builder().id(10L).user(employee).requestType(LetterRequestType.EMPLOYMENT_VERIFICATION)
                .status(VacationStatus.SUBMITTED).requestedFullName("Sam Lee")
                .requestedJobTitle("Engineer").employmentStartDate(LocalDate.of(2023, 1, 1)).build();
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(requests.findById(10L)).thenReturn(Optional.of(letter));
        when(requests.save(letter)).thenReturn(letter);
        var review = new LetterReviewRequest("Sam Lee", "Senior Engineer", LocalDate.of(2022, 1, 1), "Verified with HR");

        var approved = service.approveLetterRequest(10L, 2L, review);
        assertEquals(VacationStatus.APPROVED, approved.getStatus());
        assertTrue(approved.getLetterPreview().contains("Senior Engineer"));
        assertTrue(approved.getLetterPreview().contains("January 1, 2022"));
        employee.setJobTitle("Different profile title");
        assertTrue(service.getRequest(10L, 1L, false).getLetterPreview().contains("Senior Engineer"));

        letter.setStatus(VacationStatus.SUBMITTED);
        employee.setRole(UserRole.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        assertThrows(IllegalArgumentException.class, () -> service.approveLetterRequest(10L, 1L, review));
    }
}
