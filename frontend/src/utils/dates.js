import { format, parseISO, isValid } from 'date-fns';

// parseISO preserves calendar dates in local time and honors offsets on timestamps.
export function formatDate(value) {
  if (!value) return '-';
  const date = parseISO(value);
  return isValid(date) ? format(date, 'MMM dd, yyyy') : '-';
}
