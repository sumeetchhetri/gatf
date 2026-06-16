interface Series {
  points: Array<{ x: number; y: number }>;
  color: string;
  label?: string;
}

interface Props {
  series: Series[];
  width?: number;
  height?: number;
  // optional shared x-domain (else derived from data)
  xMin?: number;
  xMax?: number;
  // vertical marker (e.g. current scrubber time) in x-domain units
  marker?: number;
}

// Minimal multi-series line chart in inline SVG — no chart lib.
export function Sparkline({ series, width = 640, height = 160, xMin, xMax, marker }: Props) {
  const all = series.flatMap((s) => s.points);
  if (all.length === 0) {
    return <div className="muted small">no samples</div>;
  }
  const padL = 44;
  const padR = 8;
  const padT = 8;
  const padB = 18;
  const x0 = xMin ?? Math.min(...all.map((p) => p.x));
  const x1 = xMax ?? Math.max(...all.map((p) => p.x));
  const y0 = 0;
  const y1 = Math.max(1, ...all.map((p) => p.y));
  const xw = x1 - x0 || 1;
  const yw = y1 - y0 || 1;
  const px = (x: number) => padL + ((x - x0) / xw) * (width - padL - padR);
  const py = (y: number) => height - padB - ((y - y0) / yw) * (height - padT - padB);

  const yTicks = 3;
  const ticks = Array.from({ length: yTicks + 1 }, (_, i) => (y1 / yTicks) * i);
  const fmtY = (v: number) => (v >= 1000 ? `${(v / 1000).toFixed(0)}k` : `${v.toFixed(0)}`);

  return (
    <svg width={width} height={height} className="spark">
      {ticks.map((t, i) => (
        <g key={i}>
          <line x1={padL} y1={py(t)} x2={width - padR} y2={py(t)} className="grid" />
          <text x={padL - 6} y={py(t) + 3} className="axis" textAnchor="end">
            {fmtY(t)}
          </text>
        </g>
      ))}
      {series.map((s, si) => {
        const d = s.points
          .map((p, i) => `${i === 0 ? 'M' : 'L'}${px(p.x).toFixed(1)},${py(p.y).toFixed(1)}`)
          .join(' ');
        return <path key={si} d={d} fill="none" stroke={s.color} strokeWidth={1.5} />;
      })}
      {marker != null && marker >= x0 && marker <= x1 && (
        <line x1={px(marker)} y1={padT} x2={px(marker)} y2={height - padB} className="marker" />
      )}
    </svg>
  );
}
