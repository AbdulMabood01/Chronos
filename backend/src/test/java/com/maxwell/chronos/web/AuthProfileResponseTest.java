package com.maxwell.chronos.web;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthProfileResponseTest {
    @Test void signedInUserCanLoadSubmittedPersonalDetails() {
        var users = mock(UserService.class);
        var access = mock(CompanyAccessService.class);
        var user = User.builder().id(1L).email("employee@example.com")
            .isActive(true).passwordHash("stored-hash").gender("Female")
            .race("Asian").ethnicity("Test ethnicity").profileDetailsSubmitted(true).build();
        when(users.findUserEntityByEmail(user.getEmail())).thenReturn(user);
        var jwt = Jwt.withTokenValue("test").header("alg", "HS256")
            .claim("preferred_username", user.getEmail()).build();
        var controller = new AuthController(users, mock(ProjectService.class), mock(AuthSessionService.class), access);
        var response = controller.getCurrentUser(jwt);
        assertEquals(200, response.getStatusCode().value());
        var profile = response.getBody();
        assertNotNull(profile);
        assertEquals("Female", profile.getGender());
        assertEquals("Asian", profile.getRace());
        assertEquals("Test ethnicity", profile.getEthnicity());
        assertTrue(profile.getProfileDetailsSubmitted());
    }
}
