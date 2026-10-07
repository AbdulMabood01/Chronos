package com.maxwell.chronos.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Nationwide U.S. federal holidays under OPM's Monday-Friday observance rules. */
public final class FederalHolidays {
    private FederalHolidays() {}
    public static Set<LocalDate> dates(int year) {
        Set<LocalDate> result=new HashSet<>();
        for(int y=year-1;y<=year+1;y++) {
            for(int[] pair:new int[][]{{1,1},{6,19},{7,4},{11,11},{12,25}}) {
                LocalDate actual=LocalDate.of(y,pair[0],pair[1]);
                LocalDate observed=switch(actual.getDayOfWeek()) {case SATURDAY->actual.minusDays(1);case SUNDAY->actual.plusDays(1);default->actual;};
                if(actual.getYear()==year)result.add(actual);
                if(observed.getYear()==year)result.add(observed);
            }
        }
        result.add(nth(year,1,DayOfWeek.MONDAY,3));
        result.add(nth(year,2,DayOfWeek.MONDAY,3));
        result.add(LocalDate.of(year,5,31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        result.add(nth(year,9,DayOfWeek.MONDAY,1));
        result.add(nth(year,10,DayOfWeek.MONDAY,2));
        result.add(nth(year,11,DayOfWeek.THURSDAY,4));
        return result;
    }
    private static LocalDate nth(int year,int month,DayOfWeek day,int n) {
        return LocalDate.of(year,month,1).with(TemporalAdjusters.dayOfWeekInMonth(n,day));
    }
    public static Set<LocalDate> between(LocalDate start,LocalDate end) {
        Set<LocalDate> result=new HashSet<>();
        for(int year=start.getYear();year<=end.getYear();year++)result.addAll(dates(year));
        return result;
    }
    public static long workingDays(LocalDate start,LocalDate end) {
        Set<LocalDate> holidays=between(start,end);
        return start.datesUntil(end.plusDays(1)).filter(d->d.getDayOfWeek().getValue()<6&&!holidays.contains(d)).count();
    }
}
