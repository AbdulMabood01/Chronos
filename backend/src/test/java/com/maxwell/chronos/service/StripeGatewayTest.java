package com.maxwell.chronos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class StripeGatewayTest {
    final String secret="test-signature-fixture-only";
    final StripeGateway gateway=new StripeGateway(new ObjectMapper(),"sk_test_fixture",secret,"http://localhost:5173",true,false,false);
    String signature(byte[] raw,long timestamp)throws Exception{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal(raw));}
    @Test void signatureUsesRawBytesRejectsTamperingAndAcceptsRotation()throws Exception{
        byte[] bytes="{ \"id\":\"evt_test\",\"livemode\":false }".getBytes(StandardCharsets.UTF_8);long timestamp=Instant.now().getEpochSecond();String signature=signature(bytes,timestamp);
        assertEquals("evt_test",gateway.verify(bytes,signature).path("id").asText());
        assertDoesNotThrow(()->gateway.verify(bytes,signature+",v1="+"0".repeat(64)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->gateway.verify("{}".getBytes(StandardCharsets.UTF_8),signature));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->gateway.verify(bytes,signature(bytes,timestamp-600)));
    }
    @Test void environmentAccountAndLiveOptInAreEnforced()throws Exception{
        for(String payload:new String[]{"{\"id\":\"evt_test\",\"livemode\":true}","{\"id\":\"evt_test\",\"livemode\":false,\"account\":\"acct_other\"}"}){byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->gateway.verify(bytes,signature(bytes,Instant.now().getEpochSecond())));}
        assertFalse(new StripeGateway(new ObjectMapper(),"sk_live_fixture",secret,"http://localhost:5173",true,false,false).checkoutEnabled());
        assertFalse(new StripeGateway(new ObjectMapper()," sk_live_fixture ",secret,"http://localhost:5173",true,false,false).checkoutEnabled());
        assertFalse(new StripeGateway(new ObjectMapper(),"",secret,"http://localhost:5173",true,false,false).checkoutEnabled());
    }
}
