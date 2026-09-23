package com.maxwell.chronos.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

/** Bounded per-process protection. Use shared gateway limits when running multiple instances. */
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private final Map<String,Window> clients=new HashMap<>();
    private static class Window { long start; int count; Window(long now) { start=now; } }
    private synchronized boolean allow(String address) {
        long now=System.currentTimeMillis();
        clients.entrySet().removeIf(entry -> now-entry.getValue().start>=60_000);
        Window window=clients.get(address);
        if(window==null) {
            if(clients.size()>=10_000) return false;
            window=new Window(now); clients.put(address,window);
        }
        return ++window.count<=20;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getServletPath();
        if("POST".equals(request.getMethod()) && Set.of("/auth/login","/auth/activate","/auth/invitations/validate").contains(path)
                && !allow(request.getRemoteAddr())) {
            response.setStatus(429); response.setHeader("Retry-After","60"); return;
        }
        chain.doFilter(request,response);
    }
}
