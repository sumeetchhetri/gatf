import { useEffect, useRef } from 'react';

// A read-only code view for the debugger: line numbers, a clickable breakpoint gutter (only on
// debuggable lines), and a highlighted current line that auto-scrolls into view.
export default function DebugCodeView({
  code,
  currentLine,
  breakpoints,
  debuggableLines,
  onToggleBreakpoint,
  showBreakpoints = true,
}: {
  code: string;
  currentLine: number | null; // 1-based
  breakpoints: Set<number>; // 1-based
  debuggableLines: Set<number> | null; // 1-based; null = allow all
  onToggleBreakpoint: (line: number) => void;
  showBreakpoints?: boolean;
}) {
  const curRef = useRef<HTMLDivElement>(null);
  const lines = code.split('\n');

  useEffect(() => {
    curRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [currentLine]);

  const canBreak = (ln: number) => debuggableLines == null || debuggableLines.size === 0 || debuggableLines.has(ln);

  return (
    <div className="dbg-code mono">
      {lines.map((text, i) => {
        const ln = i + 1;
        const isCur = currentLine === ln;
        const hasBp = breakpoints.has(ln);
        return (
          <div className={'dbg-line' + (isCur ? ' dbg-cur' : '')} key={ln} ref={isCur ? curRef : undefined}>
            {showBreakpoints && (
              <span
                className={'dbg-bp' + (canBreak(ln) ? ' dbg-bp-able' : '') + (hasBp ? ' dbg-bp-on' : '')}
                title={canBreak(ln) ? (hasBp ? 'Remove breakpoint' : 'Add breakpoint') : undefined}
                onClick={() => canBreak(ln) && onToggleBreakpoint(ln)}
              >
                {hasBp ? '●' : ''}
              </span>
            )}
            <span className="dbg-ln">{ln}</span>
            <span className="dbg-txt">{text || '\u00A0'}</span>
          </div>
        );
      })}
    </div>
  );
}
