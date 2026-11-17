import type { ApplicationStatus } from './types';

/**
 * UI hints only. The server is the source of truth and enforces every one of these rules.
 * This file just lets the screen ask for a reason up front instead of failing after a click.
 */
export function reasonRequired(from: ApplicationStatus, to: ApplicationStatus): boolean {
  if (to === 'NEEDS_MORE_INFO' || to === 'APPROVED' || to === 'REJECTED' || to === 'SUSPENDED') return true;
  return from === 'SUSPENDED' && to === 'LIVE'; // reinstating
}

export function transitionLabel(from: ApplicationStatus, to: ApplicationStatus): string {
  if (to === 'IN_REVIEW') return from === 'SUBMITTED' ? 'Start review' : 'Resume review';
  if (to === 'NEEDS_MORE_INFO') return 'Request more info';
  if (to === 'APPROVED') return 'Approve';
  if (to === 'REJECTED') return 'Reject';
  if (to === 'LIVE') return from === 'SUSPENDED' ? 'Reinstate' : 'Go live';
  if (to === 'SUSPENDED') return 'Suspend';
  return to;
}

/** Destructive or hard-to-undo moves get a warning style. */
export function isDanger(to: ApplicationStatus): boolean {
  return to === 'REJECTED' || to === 'SUSPENDED';
}

export function reasonPrompt(to: ApplicationStatus): string {
  switch (to) {
    case 'NEEDS_MORE_INFO':
      return 'What does the applicant need to provide?';
    case 'APPROVED':
      return 'Why is this application being approved?';
    case 'REJECTED':
      return 'Why is this application being rejected?';
    case 'SUSPENDED':
      return 'Why is this pharmacy being suspended?';
    case 'LIVE':
      return 'Why is this pharmacy being reinstated?';
    default:
      return 'Add a note (optional)';
  }
}
