package com.maxwell.chronos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-process protection; deploy shared gateway limits across multiple instances. */
public class ContactRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private final Clock clock;
    private final Map<String, Window> clients = new HashMap<>();
    private record Window(long started, int count) {}
    public ContactRateLimitFilter() { this(Clock.systemUTC()); }
    ContactRateLimitFilter(Clock clock) { this.clock = clock; }

    private synchronized boolean allow(String address) {
        long now = clock.millis();
        clients.entrySet().removeIf(entry -> now - entry.getValue().started() >= WINDOW_MS);
        Window window = clients.get(address);
        if (window == null && clients.size() >= 10_000) return false;
        window = window == null ? new Window(now, 1) : new Window(window.started(), window.count() + 1);
        clients.put(address, window);
        return window.count() <= 5;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/contact/inquiries".equals(request.getServletPath())
                && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "900");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many inquiries. Please wait before trying again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
