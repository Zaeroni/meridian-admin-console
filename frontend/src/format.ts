import type { ApplicationStatus, CheckResult, QueueLevel, Role } from './types';

export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In Review',
  NEEDS_MORE_INFO: 'Needs More Info',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  LIVE: 'Live',
  SUSPENDED: 'Suspended',
};

export const ALL_STATUSES: ApplicationStatus[] = [
  'SUBMITTED',
  'IN_REVIEW',
  'NEEDS_MORE_INFO',
  'APPROVED',
  'REJECTED',
  'LIVE',
  'SUSPENDED',
];

/** Statuses where an application is waiting on someone. This is the default queue view. */
export const OPEN_STATUSES: ApplicationStatus[] = ['SUBMITTED', 'IN_REVIEW', 'NEEDS_MORE_INFO'];

export const ROLE_LABEL: Record<Role, string> = {
  REVIEWER: 'Reviewer',
  OPERATIONS_LEAD: 'Operations lead',
  READ_ONLY: 'Read-only',
};

export const RESULT_LABEL: Record<CheckResult, string> = {
  PENDING: 'Pending',
  PASS: 'Pass',
  FAIL: 'Fail',
};

export const QUEUE_LABEL: Record<QueueLevel, string> = {
  NONE: '',
  OK: 'On track',
  WARN: 'Getting old',
  CRITICAL: 'Overdue',
};

/** 0 -> "<1h", 5 -> "5h", 30 -> "1d 6h", 72 -> "3d". */
export function formatHours(hours: number | null): string {
  if (hours === null) return '';
  if (hours < 1) return '<1h';
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  const rest = hours % 24;
  return rest === 0 ? `${days}d` : `${days}d ${rest}h`;
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function formatDate(isoDate: string): string {
  // Dates like 2026-11-16 are plain calendar dates, so avoid time zone shifting by parsing the parts.
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export function formatMoney(cents: number): string {
  return (cents / 100).toLocaleString(undefined, { style: 'currency', currency: 'USD' });
}

/** "2026-11-16" -> "11/16" for chart labels. */
export function shortDay(isoDate: string): string {
  const [, m, d] = isoDate.split('-');
  return `${Number(m)}/${Number(d)}`;
}
