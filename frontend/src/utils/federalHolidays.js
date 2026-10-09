import {eachDayOfInterval, format, isWeekend, parseISO} from 'date-fns';

// Nationwide U.S. federal holidays, following OPM's Monday-Friday observance.
// https://www.opm.gov/policy-data-oversight/pay-leave/federal-holidays/
export function federalHolidays(year) {
  const result=new Map();
  const key=date=>format(date,'yyyy-MM-dd');
  const fixed=(y,month,day,name)=>{
    const actual=new Date(y,month-1,day), observed=new Date(actual);
    if(actual.getDay()===6)observed.setDate(observed.getDate()-1);
    if(actual.getDay()===0)observed.setDate(observed.getDate()+1);
    if(actual.getFullYear()===year)result.set(key(actual),name);
    if(observed.getFullYear()===year)result.set(key(observed),name+(key(actual)!==key(observed)?' (observed)':''));
  };
  const monday=(month,n,name)=>{const date=new Date(year,month-1,1);date.setDate(1+(8-date.getDay())%7+7*(n-1));result.set(key(date),name);};
  for(const y of [year-1,year,year+1]) {
    fixed(y,1,1,"New Year's Day");fixed(y,6,19,'Juneteenth National Independence Day');
    fixed(y,7,4,'Independence Day');fixed(y,11,11,'Veterans Day');fixed(y,12,25,'Christmas Day');
  }
  monday(1,3,'Birthday of Martin Luther King, Jr.');monday(2,3,"Washington's Birthday");
  const memorial=new Date(year,5,0);memorial.setDate(memorial.getDate()-(memorial.getDay()+6)%7);result.set(key(memorial),'Memorial Day');
  monday(9,1,'Labor Day');monday(10,2,'Columbus Day');
  const thanksgiving=new Date(year,10,1);thanksgiving.setDate(1+(11-thanksgiving.getDay())%7+21);result.set(key(thanksgiving),'Thanksgiving Day');
  return result;
}
export function leaveWorkingDates(start,end) {
  if(!start||!end||start>end)return [];
  const holidays=new Map();
  for(let y=Number(start.slice(0,4));y<=Number(end.slice(0,4));y++)for(const [date,name] of federalHolidays(y))holidays.set(date,name);
  return eachDayOfInterval({start:parseISO(start),end:parseISO(end)}).filter(date=>!isWeekend(date)&&!holidays.has(format(date,'yyyy-MM-dd')));
}
