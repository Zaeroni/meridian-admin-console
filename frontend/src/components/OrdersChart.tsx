import type { DailyOrders } from '../types';
import { formatMoney, shortDay } from '../format';

const WIDTH = 640;
const HEIGHT = 220;
const PAD = { top: 16, right: 12, bottom: 28, left: 36 };

/** A small hand-built SVG bar chart: one bar per day. Hover a bar for the exact numbers. */
export function OrdersChart({ days }: { days: DailyOrders[] }) {
  const max = Math.max(1, ...days.map((d) => d.orders));
  const innerW = WIDTH - PAD.left - PAD.right;
  const innerH = HEIGHT - PAD.top - PAD.bottom;
  const slot = innerW / days.length;
  const barW = slot * 0.62;
  const ticks = [0, Math.ceil(max / 2), max];

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label="Orders per day for the last 14 days" className="chart">
      {ticks.map((t) => {
        const y = PAD.top + innerH - (t / max) * innerH;
        return (
          <g key={t}>
            <line x1={PAD.left} x2={WIDTH - PAD.right} y1={y} y2={y} className="chart-grid" />
            <text x={PAD.left - 6} y={y + 4} textAnchor="end" className="chart-axis">
              {t}
            </text>
          </g>
        );
      })}
      {days.map((d, i) => {
        const h = (d.orders / max) * innerH;
        const x = PAD.left + i * slot + (slot - barW) / 2;
        const y = PAD.top + innerH - h;
        return (
          <g key={d.day}>
            <rect x={x} y={y} width={barW} height={Math.max(h, d.orders > 0 ? 1 : 0)} rx={2} className="chart-bar">
              <title>{`${d.day}: ${d.orders} orders, ${formatMoney(d.totalCents)}`}</title>
            </rect>
            {(i % 2 === 0 || i === days.length - 1) && (
              <text x={x + barW / 2} y={HEIGHT - 8} textAnchor="middle" className="chart-axis">
                {shortDay(d.day)}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
}
