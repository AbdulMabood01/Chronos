package com.maxwell.chronos.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactRateLimitFilterTest {
    private MockHttpServletResponse request(ContactRateLimitFilter filter, String method, String path, String ip) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path); request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> response.setStatus(204));
        return response;
    }
    @Test void sixthInquiryIsThrottledWithoutBlockingAnotherClient() throws Exception {
        var filter = new ContactRateLimitFilter();
        for (int i = 0; i < 5; i++) assertEquals(204, request(filter, "POST", "/contact/inquiries", "192.0.2.1").getStatus());
        var response = request(filter, "POST", "/contact/inquiries", "192.0.2.1");
        assertEquals(429, response.getStatus()); assertEquals("900", response.getHeader("Retry-After"));
        assertEquals(204, request(filter, "POST", "/contact/inquiries", "192.0.2.2").getStatus());
    }
    @Test void statusAndOtherEndpointsDoNotUseInquiryAllowance() throws Exception {
        var filter = new ContactRateLimitFilter();
        for (int i = 0; i < 10; i++) {
            assertEquals(204, request(filter, "GET", "/contact/status", "192.0.2.1").getStatus());
            assertEquals(204, request(filter, "POST", "/auth/login", "192.0.2.1").getStatus());
        }
        assertEquals(204, request(filter, "POST", "/contact/inquiries", "192.0.2.1").getStatus());
    }
    @Test void clientCanSubmitAgainAfterWindowExpires() throws Exception {
        Clock clock = mock(Clock.class); AtomicLong now = new AtomicLong(100);
        when(clock.millis()).thenAnswer(invocation -> now.get());
        var filter = new ContactRateLimitFilter(clock);
        for (int i = 0; i < 5; i++) request(filter, "POST", "/contact/inquiries", "192.0.2.1");
        assertEquals(429, request(filter, "POST", "/contact/inquiries", "192.0.2.1").getStatus());
        now.addAndGet(900_000);
        assertEquals(204, request(filter, "POST", "/contact/inquiries", "192.0.2.1").getStatus());
    }
}
