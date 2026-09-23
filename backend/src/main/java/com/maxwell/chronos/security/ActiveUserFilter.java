package com.maxwell.chronos.security;

import com.maxwell.chronos.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Recheck account status even when the caller still has an unexpired JWT. */
public class ActiveUserFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public ActiveUserFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            String email = auth.getToken().getClaimAsString("preferred_username");
            var user = email == null ? null : users.findByEmail(email).orElse(null);
            if (email == null || auth.getToken().getSubject() == null
                    || user == null
                    || (user != null && (!"ACTIVE".equals(user.getAccountStatus())
                        || !auth.getToken().getSubject().equals(user.getEntraId())))) {
                response.sendError(403, "Account is inactive or unavailable");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
