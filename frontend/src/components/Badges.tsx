import type { ApplicationStatus, CheckResult, QueueLevel } from '../types';
import { QUEUE_LABEL, RESULT_LABEL, STATUS_LABEL, formatHours } from '../format';

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>;
}

/** Shows how long an open application has been waiting. Colour plus text, so it is not colour alone. */
export function QueueBadge({ hours, level }: { hours: number | null; level: QueueLevel }) {
  if (level === 'NONE' || hours === null) return <span className="muted">n/a</span>;
  return (
    <span className={`badge queue-${level.toLowerCase()}`} title={QUEUE_LABEL[level]}>
      {formatHours(hours)}
      <span className="badge-note"> {QUEUE_LABEL[level]}</span>
    </span>
  );
}

export function ResultBadge({ result }: { result: CheckResult }) {
  return <span className={`badge result-${result.toLowerCase()}`}>{RESULT_LABEL[result]}</span>;
}
