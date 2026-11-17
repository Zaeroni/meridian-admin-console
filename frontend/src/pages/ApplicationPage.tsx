import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/ErrorBox';
import { QueueBadge, ResultBadge, StatusBadge } from '../components/Badges';
import { STATUS_LABEL, formatDate, formatDateTime } from '../format';
import { isDanger, reasonPrompt, reasonRequired, transitionLabel } from '../rules';
import type { ApplicationDetail, ApplicationStatus, DocumentType } from '../types';

const DOC_LABEL: Record<DocumentType, string> = {
  PHARMACY_LICENSE: 'Pharmacy license',
  BUSINESS_REGISTRATION: 'Business registration',
  OWNER_ID: 'Owner ID',
};
const ALL_DOC_TYPES = Object.keys(DOC_LABEL) as DocumentType[];

export function ApplicationPage() {
  const { id = '' } = useParams();
  const { canWrite } = useAuth();
  const [app, setApp] = useState<ApplicationDetail | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setApp(null);
    api
      .application(id)
      .then((a) => !cancelled && setApp(a))
      .catch((e) => !cancelled && setError(e));
    return () => {
      cancelled = true;
    };
  }, [id]);

  /** Runs a change, shows the new state on success or the server's message on failure. */
  const act = useCallback(async (fn: () => Promise<ApplicationDetail>): Promise<boolean> => {
    setBusy(true);
    setError(null);
    try {
      setApp(await fn());
      return true;
    } catch (e) {
      setError(e);
      // A refused approval still refreshed the automatic checks on the server, so reload to show them.
      try {
        setApp(await api.application(id));
      } catch {
        // keep what we have
      }
      return false;
    } finally {
      setBusy(false);
    }
  }, [id]);

  if (!app) {
    return error ? <ErrorBox error={error} /> : <p className="muted">Loading application...</p>;
  }

  const p = app.pharmacy;
  const isOpen = app.status === 'SUBMITTED' || app.status === 'IN_REVIEW' || app.status === 'NEEDS_MORE_INFO';
  const missingDocs = ALL_DOC_TYPES.filter((t) => !app.documents.some((d) => d.docType === t));

  return (
    <>
      <p>
        <Link to="/applications">Back to review queue</Link>
      </p>
      <div className="page-head">
        <div>
          <h1>{p.businessName}</h1>
          <div className="muted">
            {p.addressLine}, {p.city}, {p.state} {p.postalCode}
          </div>
        </div>
        <div className="head-status">
          <StatusBadge status={app.status} />
          <div>
            <QueueBadge hours={app.hoursInQueue} level={app.queueLevel} />
          </div>
        </div>
      </div>

      <ErrorBox error={error} />

      {canWrite && app.availableTransitions.length > 0 && (
        <TransitionPanel app={app} busy={busy} onTransition={(to, reason) => act(() => api.transition(app.id, to, reason))} />
      )}

      <div className="two-col">
        <section className="card" aria-labelledby="checklist">
          <div className="section-head">
            <h2 id="checklist">Verification checklist</h2>
            {canWrite && isOpen && (
              <button className="btn" disabled={busy} onClick={() => act(() => api.runChecks(app.id))}>
                Re-run automatic checks
              </button>
            )}
          </div>
          <ul className="checklist">
            {app.checklist.map((c) => (
              <li key={c.key}>
                <div className="check-main">
                  <ResultBadge result={c.result} />
                  <div>
                    <strong>{c.label}</strong>
                    <div className="muted">
                      {c.automatic ? 'Automatic' : 'Manual'}
                      {c.required ? ', required' : ''}
                      {c.detail ? `: ${c.detail}` : ''}
                    </div>
                    {c.checkedBy && c.checkedAt && (
                      <div className="muted">
                        {c.checkedBy}, {formatDateTime(c.checkedAt)}
                      </div>
                    )}
                    {!c.automatic && c.key === 'BUSINESS_NAME_MATCH' && (
                      <div className="muted">
                        Business name: <strong>{p.businessName}</strong>. Name on license: <strong>{p.licenseHolderName}</strong>
                      </div>
                    )}
                  </div>
                </div>
                {!c.automatic && canWrite && app.status === 'IN_REVIEW' && (
                  <ManualCheckControls
                    busy={busy}
                    onRecord={(result, note) => act(() => api.manualCheck(app.id, c.key, result, note))}
                  />
                )}
              </li>
            ))}
          </ul>
          {!app.allRequiredChecksPass && isOpen && (
            <p className="hint">Approval stays blocked until every required check passes.</p>
          )}
        </section>

        <section className="card" aria-labelledby="documents">
          <h2 id="documents">Documents</h2>
          <ul className="doc-list">
            {app.documents.map((d) => (
              <li key={d.id}>
                <strong>{DOC_LABEL[d.docType]}</strong>
                <span className="muted">
                  {' '}
                  {d.fileName}, {formatDateTime(d.uploadedAt)}
                </span>
              </li>
            ))}
            {app.documents.length === 0 && <li className="muted">No documents uploaded yet.</li>}
          </ul>
          {missingDocs.length > 0 && <p className="muted">Missing: {missingDocs.map((t) => DOC_LABEL[t]).join(', ')}</p>}
          {canWrite && isOpen && missingDocs.length > 0 && (
            <div className="inline-form">
              {missingDocs.map((t) => (
                <button
                  key={t}
                  className="btn"
                  disabled={busy}
                  onClick={() => act(() => api.addDocument(app.id, t, `${t.toLowerCase()}.pdf`))}
                >
                  Attach {DOC_LABEL[t]}
                </button>
              ))}
            </div>
          )}
          <p className="hint">Demo build: only document records are stored, not file contents.</p>
        </section>
      </div>

      <div className="two-col">
        <section className="card" aria-labelledby="pharmacy">
          <h2 id="pharmacy">Pharmacy and license</h2>
          <dl className="facts">
            <dt>Owner</dt>
            <dd>
              {p.ownerName} ({p.ownerEmail})
            </dd>
            <dt>Phone</dt>
            <dd>{p.phone ?? 'n/a'}</dd>
            <dt>License number</dt>
            <dd>
              {p.licenseNumber} ({p.licenseState})
            </dd>
            <dt>Name on license</dt>
            <dd>{p.licenseHolderName}</dd>
            <dt>License expires</dt>
            <dd>{formatDate(p.licenseExpiration)}</dd>
            <dt>Delivery radius</dt>
            <dd>{p.deliveryRadiusMiles === null ? 'Not set' : `${p.deliveryRadiusMiles} miles`}</dd>
            <dt>Storefront</dt>
            <dd>
              {p.storefrontName} (<code>{p.storefrontSlug}</code>)
            </dd>
            <dt>Assigned reviewer</dt>
            <dd>{app.assignedReviewer ?? 'Unassigned'}</dd>
            <dt>Submitted</dt>
            <dd>{formatDateTime(app.submittedAt)}</dd>
          </dl>
        </section>

        <section className="card" aria-labelledby="history">
          <h2 id="history">Status history</h2>
          <ol className="timeline">
            {app.history.map((h) => (
              <li key={h.id}>
                <div>
                  <strong>
                    {h.from ? `${STATUS_LABEL[h.from]} to ${STATUS_LABEL[h.to]}` : STATUS_LABEL[h.to]}
                  </strong>
                </div>
                <div className="muted">
                  {h.actor ?? 'System'}, {formatDateTime(h.at)}
                </div>
                {h.reason && <div className="reason">{h.reason}</div>}
              </li>
            ))}
          </ol>
        </section>
      </div>
    </>
  );
}

function ManualCheckControls({
  busy,
  onRecord,
}: {
  busy: boolean;
  onRecord: (result: 'PASS' | 'FAIL', note: string) => void;
}) {
  const [note, setNote] = useState('');
  return (
    <div className="manual-check">
      <input placeholder="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
      <button className="btn btn-pass" disabled={busy} onClick={() => onRecord('PASS', note)}>
        Mark pass
      </button>
      <button className="btn btn-danger" disabled={busy} onClick={() => onRecord('FAIL', note)}>
        Mark fail
      </button>
    </div>
  );
}

function TransitionPanel({
  app,
  busy,
  onTransition,
}: {
  app: ApplicationDetail;
  busy: boolean;
  onTransition: (to: ApplicationStatus, reason: string) => Promise<boolean>;
}) {
  const [target, setTarget] = useState<ApplicationStatus | null>(null);
  const [reason, setReason] = useState('');

  const required = target !== null && reasonRequired(app.status, target);
  const blockedApproval = (to: ApplicationStatus) => to === 'APPROVED' && !app.allRequiredChecksPass;
  const failing = app.checklist.filter((c) => c.required && c.result !== 'PASS');

  async function confirm() {
    if (!target) return;
    const ok = await onTransition(target, reason);
    if (ok) {
      setTarget(null);
      setReason('');
    }
  }

  return (
    <section className="card actions" aria-label="Actions">
      <h2>Actions</h2>
      <div className="button-row">
        {app.availableTransitions.map((to) => (
          <button
            key={to}
            className={`btn ${isDanger(to) ? 'btn-danger' : 'btn-primary'}`}
            disabled={busy || blockedApproval(to)}
            aria-pressed={target === to}
            title={blockedApproval(to) ? 'Blocked until every required check passes' : undefined}
            onClick={() => {
              // Routine moves (start or resume review) apply straight away. Anything that needs a
              // reason, and going live, opens the confirm step first.
              if (reasonRequired(app.status, to) || to === 'LIVE') {
                setTarget(to);
                setReason('');
              } else {
                void onTransition(to, '');
              }
            }}
          >
            {transitionLabel(app.status, to)}
          </button>
        ))}
      </div>

      {app.availableTransitions.includes('APPROVED') && !app.allRequiredChecksPass && (
        <p className="hint">
          Approve is blocked. Not passing: {failing.map((c) => c.label).join('; ')}.
        </p>
      )}

      {target && (
        <div className="reason-form">
          <label>
            {reasonPrompt(target)} {required ? <span className="req">(required)</span> : null}
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              maxLength={1000}
              autoFocus
            />
          </label>
          <div className="button-row">
            <button
              className={`btn ${isDanger(target) ? 'btn-danger' : 'btn-primary'}`}
              disabled={busy || (required && reason.trim() === '')}
              onClick={confirm}
            >
              Confirm: {transitionLabel(app.status, target)}
            </button>
            <button className="btn" disabled={busy} onClick={() => setTarget(null)}>
              Cancel
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
