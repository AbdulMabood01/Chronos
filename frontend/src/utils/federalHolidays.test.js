import {it,expect} from 'vitest';
import {federalHolidays,leaveWorkingDates} from './federalHolidays';
it('includes observed holidays across year boundaries',()=>{
  expect(federalHolidays(2026).get('2026-07-03')).toBe('Independence Day (observed)');
  expect(federalHolidays(2021).get('2021-12-31')).toBe("New Year's Day (observed)");
  expect(federalHolidays(2026).get('2026-11-26')).toBe('Thanksgiving Day');
});
it('excludes holidays and weekends from leave duration',()=>{
  expect(leaveWorkingDates('2026-10-09','2026-10-12')).toHaveLength(1);
  expect(leaveWorkingDates('2026-07-03','2026-07-06')).toHaveLength(1);
  expect(leaveWorkingDates('2026-12-24','2027-01-04')).toHaveLength(6);
});
