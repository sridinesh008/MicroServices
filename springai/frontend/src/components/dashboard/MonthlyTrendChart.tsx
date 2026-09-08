import type { MonthTotal } from "../../api/types";

const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export default function MonthlyTrendChart({ data }: { data: MonthTotal[] }) {
  if (data.length === 0) {
    return <p className="text-sm text-ink-muted py-6 text-center">No data for this range.</p>;
  }

  const sorted = [...data].sort((a, b) => a.year - b.year || a.month - b.month);
  const values = sorted.map((d) => Number(d.total));
  const max = Math.max(...values, 1);

  const width = 480;
  const height = 160;
  const padTop = 16;
  const padBottom = 24;
  const padX = 8;
  const plotHeight = height - padTop - padBottom;
  const step = sorted.length > 1 ? (width - padX * 2) / (sorted.length - 1) : 0;

  const points = sorted.map((d, i) => {
    const x = padX + step * i;
    const y = padTop + plotHeight - (Number(d.total) / max) * plotHeight;
    return { x, y, label: `${MONTH_NAMES[d.month - 1]} ${d.year}`, value: Number(d.total) };
  });

  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");
  const areaPath = `${linePath} L ${points[points.length - 1].x} ${padTop + plotHeight} L ${points[0].x} ${padTop + plotHeight} Z`;
  const last = points[points.length - 1];

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full" role="img" aria-label="Monthly spend trend">
      <line
        x1={padX}
        y1={padTop + plotHeight}
        x2={width - padX}
        y2={padTop + plotHeight}
        stroke="var(--baseline)"
        strokeWidth={1}
      />
      <path d={areaPath} fill="var(--series-1)" opacity={0.1} />
      <path d={linePath} fill="none" stroke="var(--series-1)" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
      {points.map((p, i) => (
        <g key={i}>
          <circle cx={p.x} cy={p.y} r={4} fill="var(--series-1)" stroke="var(--surface-1)" strokeWidth={2}>
            <title>{`${p.label}: ${p.value.toFixed(2)}`}</title>
          </circle>
          <text x={p.x} y={height - 6} fontSize={9} textAnchor="middle" fill="var(--text-muted)">
            {MONTH_NAMES[sorted[i].month - 1]}
          </text>
        </g>
      ))}
      <text x={last.x} y={last.y - 10} fontSize={11} textAnchor="end" fill="var(--text-secondary)">
        {last.value.toFixed(2)}
      </text>
    </svg>
  );
}
