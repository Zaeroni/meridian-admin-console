import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { ErrorBox } from '../components/ErrorBox';
import { QueueBadge, StatusBadge } from '../components/Badges';
import { ALL_STATUSES, OPEN_STATUSES, STATUS_LABEL, formatDateTime } from '../format';
import type { ApplicationStatus, ApplicationSummary, Page } from '../types';

const PAGE_SIZE = 15;

const SORTS: { value: string; label: string }[] = [
  { value: 'timeInQueue', label: 'Time in queue' },
  { value: 'submittedAt', label: 'Submitted date' },
  { value: 'businessName', label: 'Pharmacy name' },
  { value: 'status', label: 'Status' },
];

/**
 * The review queue. All filter state lives in the URL, so a filtered view can be bookmarked,
 * shared, and survives a refresh or the browser back button.
 */
export function QueuePage() {
  const [params, setParams] = useSearchParams();

  const statusParam = params.getAll('status') as ApplicationStatus[];
  const status = statusParam.length > 0 ? statusParam : OPEN_STATUSES;
  const q = params.get('q') ?? '';
  const mine = params.get('mine') === 'true';
  const sort = params.get('sort') ?? 'timeInQueue';
  const dir = (params.get('dir') ?? 'desc') as 'asc' | 'desc';
  const page = Number(params.get('page') ?? '0');

  const [search, setSearch] = useState(q);
  const [data, setData] = useState<Page<ApplicationSummary> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  function update(changes: Record<string, string | string[] | null>, resetPage = true) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      next.delete(key);
      if (Array.isArray(value)) value.forEach((v) => next.append(key, v));
      else if (value !== null && value !== '') next.set(key, value);
    }
    if (resetPage) next.delete('page');
    setParams(next, { replace: true });
  }

  // Wait for a pause in typing before searching.
  useEffect(() => {
    if (search === q) return;
    const timer = setTimeout(() => update({ q: search }), 300);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const statusKey = status.join(',');
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api
      .queue({ status, q, assignedToMe: mine, sort, dir, page, size: PAGE_SIZE })
      .then((result) => {
        if (cancelled) return;
        setData(result);
        setError(null);
      })
      .catch((e) => !cancelled && setError(e))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // statusKey stands in for the status array so the effect does not re-run on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusKey, q, mine, sort, dir, page]);

  function toggleStatus(s: ApplicationStatus) {
    const next = status.includes(s) ? status.filter((x) => x !== s) : [...status, s];
    // An empty selection would be confusing, so fall back to the default view.
    update({ status: next.length === 0 ? [] : next });
  }

  const allSelected = status.length === ALL_STATUSES.length;

  return (
    <>
      <h1>Review queue</h1>

      <div className="card filters">
        <div className="filter-row">
          <label className="grow">
            Search
            <input
              type="search"
              placeholder="Pharmacy, owner, license number or city"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <label>
            Sort by
            <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
          <button
            className="btn"
            onClick={() => update({ dir: dir === 'desc' ? 'asc' : 'desc' })}
            title="Reverse the sort order"
          >
            {dir === 'desc' ? 'Descending' : 'Ascending'}
          </button>
          <label className="check">
            <input type="checkbox" checked={mine} onChange={(e) => update({ mine: e.target.checked ? 'true' : null })} />
            Assigned to me
          </label>
        </div>

        <div className="chip-row" role="group" aria-label="Filter by status">
          {ALL_STATUSES.map((s) => (
            <button
              key={s}
              className={`chip ${status.includes(s) ? 'chip-on' : ''}`}
              aria-pressed={status.includes(s)}
              onClick={() => toggleStatus(s)}
            >
              {STATUS_LABEL[s]}
            </button>
          ))}
          <span className="chip-gap" />
          <button className="link-button" onClick={() => update({ status: OPEN_STATUSES })}>
            Open only
          </button>
          <button className="link-button" disabled={allSelected} onClick={() => update({ status: ALL_STATUSES })}>
            All statuses
          </button>
        </div>
      </div>

      <ErrorBox error={error} />

      <div className="card table-card" aria-busy={loading}>
        <table>
          <thead>
            <tr>
              <th>Pharmacy</th>
              <th>Location</th>
              <th>Status</th>
              <th>Assigned to</th>
              <th>Submitted</th>
              <th>Time in queue</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((a) => (
              <tr key={a.id}>
                <td>
                  <Link to={`/applications/${a.id}`} className="row-link">
                    {a.businessName}
                  </Link>
                  <div className="muted">{a.ownerName}</div>
                </td>
                <td>
                  {a.city}, {a.state}
                </td>
                <td>
                  <StatusBadge status={a.status} />
                </td>
                <td>{a.assignedReviewer ?? <span className="muted">Unassigned</span>}</td>
                <td>{formatDateTime(a.submittedAt)}</td>
                <td>
                  <QueueBadge hours={a.hoursInQueue} level={a.queueLevel} />
                </td>
              </tr>
            ))}
            {data && data.items.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No applications match these filters.
                </td>
              </tr>
            )}
            {!data && !error && (
              <tr>
                <td colSpan={6} className="empty">
                  Loading...
                </td>
              </tr>
            )}
          </tbody>
        </table>

        {data && data.totalElements > 0 && (
          <div className="pager">
            <span className="muted">
              {data.totalElements} result{data.totalElements === 1 ? '' : 's'}, page {data.page + 1} of{' '}
              {Math.max(1, data.totalPages)}
            </span>
            <div>
              <button className="btn" disabled={page <= 0} onClick={() => update({ page: String(page - 1) }, false)}>
                Previous
              </button>
              <button
                className="btn"
                disabled={page + 1 >= data.totalPages}
                onClick={() => update({ page: String(page + 1) }, false)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
