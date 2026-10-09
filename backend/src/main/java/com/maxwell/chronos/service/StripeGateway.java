package com.maxwell.chronos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Hosted, one-off payments only. No browser callback can grant capacity. */
@Service
public class StripeGateway {
    private final ObjectMapper json;
    private final String key,secret,origin;
    private final boolean enabled,liveEnabled,tax;
    public StripeGateway(ObjectMapper json,@Value("${billing.stripe.secret-key:}") String key,
        @Value("${billing.stripe.webhook-secret:}") String secret,@Value("${billing.web-origin:http://localhost:5173}") String origin,
        @Value("${billing.checkout-enabled:false}") boolean enabled,@Value("${billing.stripe.live-enabled:false}") boolean liveEnabled,
        @Value("${billing.stripe.automatic-tax:false}") boolean tax){
        this.json=json;this.key=key.trim();this.secret=secret.trim();this.origin=origin.trim().replaceAll("/$","");this.enabled=enabled;this.liveEnabled=liveEnabled;this.tax=tax;
    }
    public boolean live(){return key.startsWith("sk_live_")||key.startsWith("rk_live_");}
    public boolean configured(){return (key.startsWith("sk_test_")||key.startsWith("rk_test_")||live())&&!secret.isBlank()&&(!live()||liveEnabled);}
    public boolean checkoutEnabled(){return enabled&&configured();}
    private void requireConfigured(){if(!configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Stripe is not configured. No payment was taken.");}
    private JsonNode request(String path,LinkedMultiValueMap<String,String> form,String idempotency){
        requireConfigured();try{
            var http=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
            var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(http);factory.setReadTimeout(java.time.Duration.ofSeconds(30));
            var client=RestClient.builder().requestFactory(factory).baseUrl("https://api.stripe.com/v1").defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+key)
                .defaultHeader("Stripe-Version","2025-02-24.acacia").build();
            String body=form==null?client.get().uri(path).retrieve().body(String.class):client.post().uri(path)
                .header("Idempotency-Key",idempotency).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class);
            return json.readTree(body);
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Stripe could not complete the request. Retry safely or contact billing support.");}
    }
    public JsonNode checkout(Map<String,Object> p,String email){
        if(!checkoutEnabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Checkout is disabled until Stripe is configured. No payment was taken.");
        var f=new LinkedMultiValueMap<String,String>();String id=p.get("id").toString(),company=p.get("company_id").toString();
        f.add("mode","payment");f.add("client_reference_id",id);f.add("metadata[purchase_id]",id);f.add("metadata[company_id]",company);
        f.add("customer_email",email);f.add("billing_address_collection","required");
        f.add("success_url",origin+"/billing?company="+company+"&purchase="+id);
        f.add("cancel_url",origin+"/billing?company="+company+"&purchase="+id+"&canceled=1");
        f.add("expires_at",Long.toString(CompanyEntitlements.instant(p.get("expires_at")).getEpochSecond()));
        f.add("line_items[0][price_data][currency]","usd");f.add("line_items[0][price_data][unit_amount]",p.get("amount_cents").toString());
        f.add("line_items[0][price_data][product_data][name]","Chronos "+p.get("plan_key")+" — "+p.get("kind")+" ("+p.get("term_months")+" months)");
        f.add("line_items[0][quantity]","1");if(tax){f.add("automatic_tax[enabled]","true");f.add("line_items[0][price_data][tax_behavior]","exclusive");}
        return request("/checkout/sessions",f,"chronos-checkout-"+id);
    }
    public JsonNode session(String id){if(!id.matches("cs_[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid provider session");return request("/checkout/sessions/"+id,null,null);}
    public JsonNode expire(String id){if(!id.matches("cs_[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid provider session");try{return request("/checkout/sessions/"+id+"/expire",new LinkedMultiValueMap<>(),"chronos-expire-"+id);}catch(ResponseStatusException ex){return session(id);}}
    public Instant paidAt(String intent){if(!intent.matches("pi_[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid payment");var payment=request("/payment_intents/"+intent+"?expand[]=latest_charge",null,null);if(!payment.path("status").asText().equals("succeeded")||!payment.path("latest_charge").has("created"))throw new IllegalArgumentException("Payment confirmation unavailable");return Instant.ofEpochSecond(payment.path("latest_charge").path("created").asLong());}
    public JsonNode refund(String intent,UUID purchase){var f=new LinkedMultiValueMap<String,String>();f.add("payment_intent",intent);f.add("metadata[purchase_id]",purchase.toString());return request("/refunds",f,"chronos-refund-"+purchase);}
    public JsonNode refundStatus(String id){if(!id.matches("re_[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid refund");return request("/refunds/"+id,null,null);}
    public JsonNode verify(byte[] raw,String signature){
        requireConfigured();try{
            if(raw.length>1048576||signature==null||signature.length()>4096)throw new IllegalArgumentException();
            String timestamp=null;List<String> hashes=new ArrayList<>();
            for(String part:signature.split(",")){String[] pair=part.trim().split("=",2);if(pair.length==2){if(pair[0].equals("t")){if(timestamp!=null)throw new IllegalArgumentException();timestamp=pair[1];}if(pair[0].equals("v1"))hashes.add(pair[1]);}}
            long seconds=Long.parseLong(timestamp);if(Math.abs(Instant.now().getEpochSecond()-seconds)>300)throw new IllegalArgumentException();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));byte[] expected=mac.doFinal(raw);boolean valid=false;
            for(String hash:hashes)if(hash.matches("[a-fA-F0-9]{64}")&&MessageDigest.isEqual(expected,HexFormat.of().parseHex(hash)))valid=true;
            if(!valid)throw new IllegalArgumentException();JsonNode event=json.readTree(raw);
            if(!event.has("livemode")||event.path("livemode").asBoolean()!=live()||event.hasNonNull("account")||!event.path("id").asText().startsWith("evt_"))throw new IllegalArgumentException();
            return event;
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Stripe webhook signature or environment");}
    }
}
