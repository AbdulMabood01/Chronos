package com.maxwell.chronos.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PlanCatalogTest {
    final PlanCatalog catalog=new PlanCatalog();
    @Test void allPublishedPrepaidAmountsAndAddOnsAreExact(){
        long[][] totals={{44700,80460,143040},{89700,161460,287040},{179700,323460,575040}};
        String[] plans={"PRO","PRO_PLUS","PRO_MAX"};int[] months={3,6,12};
        for(int p=0;p<3;p++)for(int t=0;t<3;t++){
            var price=catalog.price(plans[p],months[t],0);assertEquals(totals[p][t],price.totalCents());
            assertEquals(totals[p][t]+10*new long[]{1200,2160,3840}[t],catalog.price(plans[p],months[t],10).totalCents());
            assertEquals(price.subtotalCents(),price.totalCents()+price.discountCents());
        }
        assertThrows(IllegalArgumentException.class,()->catalog.price("FREE",3,0));
        assertThrows(IllegalArgumentException.class,()->catalog.price("PRO",1,0));
        assertThrows(IllegalArgumentException.class,()->catalog.price("PRO",3,-1));
    }
    @Test void calendarMonthEndAndLeapYearDatesAndProration(){
        assertEquals(Instant.parse("2027-04-30T13:22:00Z"),catalog.end(Instant.parse("2027-01-31T13:22:00Z"),3));
        assertEquals(Instant.parse("2025-02-28T00:00:00Z"),catalog.end(Instant.parse("2024-02-29T00:00:00Z"),12));
        Instant start=Instant.parse("2026-01-01T00:00:00Z"),end=start.plusSeconds(10000);
        assertEquals(19200,catalog.prorate(10,3840,start,end,start.plusSeconds(5000)));
        assertEquals(1,catalog.prorate(1,1,start,end,start.plusSeconds(5000)));
        assertThrows(IllegalArgumentException.class,()->catalog.prorate(1,3840,start,end,end));
    }
}
