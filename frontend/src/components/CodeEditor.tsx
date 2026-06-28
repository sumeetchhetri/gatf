import { useLayoutEffect, useRef } from 'react';

export interface EditorMarker {
  line: number; // 1-based
  message: string;
}

// A dependency-free code editor: a monospace textarea with a synced line-number gutter and
// per-line error markers. A real syntax editor (CodeMirror 6 + seleasy grammar) lands in Slice C2.
export default function CodeEditor({
  value,
  onChange,
  markers = [],
  readOnly = false,
  gotoLine,
}: {
  value: string;
  onChange?: (v: string) => void;
  markers?: EditorMarker[];
  readOnly?: boolean;
  gotoLine?: number | null;
}) {
  const taRef = useRef<HTMLTextAreaElement>(null);
  const gutterRef = useRef<HTMLDivElement>(null);
  const lineCount = Math.max(1, value.split('\n').length);
  const markerMap = new Map<number, string>();
  for (const m of markers) {
    const prev = markerMap.get(m.line);
    markerMap.set(m.line, prev ? prev + '\n' + m.message : m.message);
  }

  const syncScroll = () => {
    if (gutterRef.current && taRef.current) gutterRef.current.scrollTop = taRef.current.scrollTop;
  };

  // Jump the caret/scroll to a requested line (from clicking a problem).
  useLayoutEffect(() => {
    if (gotoLine == null || !taRef.current) return;
    const ta = taRef.current;
    const lines = value.split('\n');
    let pos = 0;
    for (let i = 0; i < Math.min(gotoLine - 1, lines.length); i++) pos += lines[i].length + 1;
    ta.focus();
    ta.setSelectionRange(pos, pos);
    // approximate scroll: line height ~ 19px
    ta.scrollTop = Math.max(0, (gotoLine - 3) * 19);
    syncScroll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gotoLine]);

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Tab' && !readOnly) {
      e.preventDefault();
      const ta = e.currentTarget;
      const s = ta.selectionStart;
      const en = ta.selectionEnd;
      const next = value.slice(0, s) + '  ' + value.slice(en);
      onChange?.(next);
      requestAnimationFrame(() => ta.setSelectionRange(s + 2, s + 2));
    }
  };

  return (
    <div className="ce">
      <div className="ce-gutter" ref={gutterRef} aria-hidden>
        {Array.from({ length: lineCount }, (_, i) => {
          const ln = i + 1;
          const msg = markerMap.get(ln);
          return (
            <div key={ln} className={'ce-ln' + (msg ? ' ce-ln-err' : '')} title={msg || undefined}>
              {ln}
            </div>
          );
        })}
      </div>
      <textarea
        ref={taRef}
        className="ce-ta mono"
        spellCheck={false}
        wrap="off"
        readOnly={readOnly}
        value={value}
        onChange={(e) => onChange?.(e.target.value)}
        onScroll={syncScroll}
        onKeyDown={onKeyDown}
      />
    </div>
  );
}
