package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {
    UserRepository users=mock(UserRepository.class);
    EmployeeInvitationRepository invitations=mock(EmployeeInvitationRepository.class);
    InvitationEmailService email=mock(InvitationEmailService.class);
    BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    OnboardingService service=new OnboardingService(users,invitations,encoder,email,72);
    User user;
    EmployeeInvitation invitation;
    String token="a".repeat(43);
    @BeforeEach void setup() {
        user=User.builder().id(1L).firstName("Alice").lastName("Smith").email("alice@example.com")
                .isActive(true).role(UserRole.EMPLOYEE).build();
        invitation=new EmployeeInvitation(); invitation.setEmployeeId(1L); invitation.setTokenHash(OnboardingService.hash(token));
        invitation.setCreatedAt(Instant.now().minusSeconds(120)); invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(user));
        when(invitations.findByEmployeeId(1L)).thenReturn(Optional.of(invitation));
        when(invitations.findEmployeeIdByTokenHash(OnboardingService.hash(token))).thenReturn(Optional.of(1L));
    }
    @Test void createsInvitedEmployeeWithNormalizedEmailAndNoPassword() {
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        User created=service.create(" Alice "," Smith ","ALICE@example.com");
        assertEquals("alice@example.com",created.getEmail()); assertEquals("Alice",created.getFirstName());
        assertEquals("INVITED",created.getAccountStatus()); assertNull(created.getPasswordHash());
        assertEquals(UserRole.EMPLOYEE,created.getRole()); assertNotNull(created.getEntraId());
    }
    @Test void rejectsDuplicateEmail() {
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(IllegalArgumentException.class,()->service.create("Alice","Smith",user.getEmail()));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void invitationIsRandomHashedAndExpiresAfterConfiguredPeriod() {
        service.invite(1L);
        ArgumentCaptor<String> raw=ArgumentCaptor.forClass(String.class);
        verify(email).send(eq(user),raw.capture(),eq(invitation.getExpiresAt()));
        assertEquals(43,raw.getValue().length()); assertNotEquals(raw.getValue(),invitation.getTokenHash());
        assertEquals(OnboardingService.hash(raw.getValue()),invitation.getTokenHash());
        assertTrue(invitation.getExpiresAt().isAfter(Instant.now().plusSeconds(71*3600)));
    }
    @Test void validInvitationDisplaysOnlyIdentity() { assertEquals(user.getEmail(),service.validate(token).email()); }
    @Test void invalidInvitationIsRejected() { assertThrows(IllegalArgumentException.class,()->service.validate("b".repeat(43))); }
    @Test void expiredInvitationIsRejected() {
        invitation.setExpiresAt(Instant.now().minusSeconds(1));
        assertTrue(assertThrows(IllegalArgumentException.class,()->service.validate(token)).getMessage().contains("expired"));
    }
    @Test void usedInvitationIsRejected() {
        invitation.setUsedAt(Instant.now());
        assertTrue(assertThrows(IllegalArgumentException.class,()->service.validate(token)).getMessage().contains("used"));
    }
    @Test void revokedInvitationIsRejected() {
        service.revoke(1L);
        assertTrue(assertThrows(IllegalArgumentException.class,()->service.validate(token)).getMessage().contains("revoked"));
    }
    @Test void inactiveEmployeeCannotActivate() {
        user.setIsActive(false); assertThrows(IllegalArgumentException.class,()->service.activate(token,"StrongPassword123"));
        assertNull(user.getPasswordHash());
    }
    @Test void resendReplacesTokenAndInvalidatesOldLink() {
        invitation.setRevokedAt(Instant.now()); service.invite(1L);
        assertNull(invitation.getRevokedAt()); assertNotEquals(OnboardingService.hash(token),invitation.getTokenHash());
        assertThrows(IllegalArgumentException.class,()->service.validate(token));
        verify(invitations).saveAndFlush(invitation);
    }
    @Test void resendsHaveCooldown() {
        invitation.setCreatedAt(Instant.now()); assertThrows(IllegalArgumentException.class,()->service.invite(1L));
        verifyNoInteractions(email);
    }
    @Test void activationHashesPasswordAndConsumesToken() {
        service.activate(token,"StrongPassword123");
        assertNotEquals("StrongPassword123",user.getPasswordHash()); assertTrue(encoder.matches("StrongPassword123",user.getPasswordHash()));
        assertEquals("ACTIVE",user.getAccountStatus()); assertNotNull(invitation.getUsedAt());
        assertThrows(IllegalArgumentException.class,()->service.activate(token,"AnotherPassword123"));
    }
    @Test void weakAndOverlongPasswordsAreRejected() {
        for(String password:new String[]{"short","alllowercase123","ALLUPPERCASE123","NoNumbersHere","Aa1"+"é".repeat(36)})
            assertThrows(IllegalArgumentException.class,()->service.activate(token,password));
        assertNull(user.getPasswordHash()); assertNull(invitation.getUsedAt());
    }
    @Test void activeEmployeeCannotBeInvitedAgain() {
        user.setPasswordHash(encoder.encode("StrongPassword123"));
        assertThrows(IllegalArgumentException.class,()->service.invite(1L)); verifyNoInteractions(email);
    }
}
