package com.maxwell.chronos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PlanCatalogBoundaryTest {
    final PlanCatalog catalog=new PlanCatalog();
    @ParameterizedTest @CsvSource({"FREE,1,7,0","PRO,3,75,14900","PRO_PLUS,7,175,29900","PRO_MAX,15,375,59900"})
    void publishedCapacity(String key,int projects,int users,long cents) {
        var plan=catalog.plan(key);assertEquals(projects,plan.projects());assertEquals(users,plan.users());assertEquals(cents,plan.monthlyCents());
    }
    @ParameterizedTest @ValueSource(ints={Integer.MIN_VALUE,-1,0,1,2,4,5,7,8,9,10,11,13,Integer.MAX_VALUE})
    void unsupportedTerms(int months) { assertThrows(IllegalArgumentException.class,()->catalog.price("PRO",months,0)); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"pro","PRO ","CUSTOM","SINGLE","ENTERPRISE","UNKNOWN"})
    void unpublishedPlans(String plan) { assertThrows(IllegalArgumentException.class,()->catalog.plan(plan)); }
    @Test void maximumSeatBoundaryIsExactForEveryPaidPlanAndTerm() {
        for(String plan:new String[]{"PRO","PRO_PLUS","PRO_MAX"})for(int months:new int[]{3,6,12})for(int seats:new int[]{0,1,99999,100000}) {
            var price=catalog.price(plan,months,seats);
            assertEquals(catalog.price(plan,months,0).totalCents()+seats*price.seatTermCents(),price.totalCents());
            assertEquals(price.subtotalCents(),price.discountCents()+price.totalCents());
        }
        for(int seats:new int[]{-1,100001,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->catalog.price("PRO_MAX",12,seats));
    }
    @Test void prorationClampsBeforeStartAndRejectsExpiredOrInvalidQuantities() {
        Instant start=Instant.parse("2026-01-01T00:00:00Z"),end=start.plusSeconds(100);
        assertEquals(1200,catalog.prorate(1,1200,start,end,start.minusSeconds(1)));
        assertEquals(12,catalog.prorate(1,1200,start,end,end.minusSeconds(1)));
        assertEquals(120000000,catalog.prorate(100000,1200,start,end,start));
        for(int quantity:new int[]{-1,0,100001})assertThrows(IllegalArgumentException.class,()->catalog.prorate(quantity,1200,start,end,start));
        assertThrows(IllegalArgumentException.class,()->catalog.prorate(1,1200,start,end,end));
        assertThrows(IllegalArgumentException.class,()->catalog.prorate(1,1200,start,start,start));
    }
}
