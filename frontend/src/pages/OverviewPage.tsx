import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { ErrorBox } from '../components/ErrorBox';
import { OrdersChart } from '../components/OrdersChart';
import { StatusBadge } from '../components/Badges';
import { ALL_STATUSES, STATUS_LABEL, formatMoney } from '../format';
import type { Overview } from '../types';

export function OverviewPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .overview()
      .then((d) => !cancelled && setData(d))
      .catch((e) => !cancelled && setError(e));
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) return <ErrorBox error={error} />;
  if (!data) return <p className="muted">Loading overview...</p>;

  const totalOrders = data.ordersPerDay.reduce((sum, d) => sum + d.orders, 0);
  const totalCents = data.ordersPerDay.reduce((sum, d) => sum + d.totalCents, 0);

  return (
    <>
      <h1>Platform overview</h1>

      <section aria-labelledby="by-status">
        <h2 id="by-status">Pharmacies by status</h2>
        <div className="stat-grid">
          {ALL_STATUSES.map((s) => (
            <Link key={s} to={`/applications?status=${s}`} className={`stat-card stat-${s.toLowerCase()}`}>
              <span className="stat-number">{data.pharmaciesByStatus[s] ?? 0}</span>
              <span className="stat-label">{STATUS_LABEL[s]}</span>
            </Link>
          ))}
          <div className="stat-card stat-total">
            <span className="stat-number">{data.totalPharmacies}</span>
            <span className="stat-label">Total</span>
          </div>
        </div>
      </section>

      <div className="two-col">
        <section className="card" aria-labelledby="orders">
          <h2 id="orders">Orders per day</h2>
          <p className="muted">
            Last 14 days: {totalOrders.toLocaleString()} orders, {formatMoney(totalCents)}
          </p>
          <OrdersChart days={data.ordersPerDay} />
        </section>

        <section className="card" aria-labelledby="attention">
          <h2 id="attention">Needs attention</h2>
          {data.needsAttention.length === 0 ? (
            <p className="muted">Nothing needs attention right now.</p>
          ) : (
            <ul className="attention-list">
              {data.needsAttention.map((item) => (
                <li key={`${item.applicationId}-${item.code}`}>
                  <span className={`badge sev-${item.severity.toLowerCase()}`}>
                    {item.severity === 'CRITICAL' ? 'Critical' : 'Warning'}
                  </span>
                  <div>
                    <Link to={`/applications/${item.applicationId}`}>{item.pharmacyName}</Link>{' '}
                    <StatusBadge status={item.status} />
                    <div className="muted">{item.message}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
