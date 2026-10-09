package com.maxwell.chronos.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

/** The server is the only price authority. Purchased snapshots survive future catalog changes. */
@Service
public class PlanCatalog {
    public static final String VERSION="2026-10-v1";
    public record Plan(String key,String name,int projects,int users,long monthlyCents) {}
    public record Price(long subtotalCents,long discountCents,long totalCents,long seatTermCents,int discountPercent) {}
    public List<Plan> plans(){return List.of(new Plan("FREE","Free",1,7,0),new Plan("PRO","Pro",3,75,14900),
        new Plan("PRO_PLUS","Pro Plus",7,175,29900),new Plan("PRO_MAX","Pro Max",15,375,59900));}
    public Plan plan(String key){return plans().stream().filter(p->p.key().equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a published plan"));}
    public int discount(int months){return switch(months){case 3->0;case 6->10;case 12->20;default->throw new IllegalArgumentException("Choose a 3, 6 or 12 month term");};}
    public Price price(String key,int months,int extra){
        var p=plan(key);if(p.monthlyCents()==0)throw new IllegalArgumentException("Free does not require a purchase");
        if(extra<0||extra>100000)throw new IllegalArgumentException("Extra seats must be between 0 and 100000");
        int discount=discount(months);long subtotal=Math.multiplyExact(p.monthlyCents()+400L*extra,months);
        long total=subtotal*(100-discount)/100;
        return new Price(subtotal,subtotal-total,total,400L*months*(100-discount)/100,discount);
    }
    public Instant end(Instant start,int months){discount(months);return start.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();}
    public long prorate(int quantity,long fullSeatCents,Instant start,Instant end,Instant now){
        if(quantity<1||quantity>100000||!end.isAfter(now)||!end.isAfter(start))throw new IllegalArgumentException("Choose extra seats during an active paid term");
        long duration=Duration.between(start,end).getSeconds(),remaining=Duration.between(now.isBefore(start)?start:now,end).getSeconds();
        return BigDecimal.valueOf(fullSeatCents).multiply(BigDecimal.valueOf(quantity)).multiply(BigDecimal.valueOf(remaining))
            .divide(BigDecimal.valueOf(duration),0,RoundingMode.HALF_UP).longValueExact();
    }
}
