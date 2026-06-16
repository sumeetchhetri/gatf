import { useRef, useState } from 'react';

export function Dropzone({
  onBytes,
  error,
}: {
  onBytes: (bytes: Uint8Array, name: string) => void;
  error: string | null;
}) {
  const [over, setOver] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const handleFile = async (file: File) => {
    const buf = await file.arrayBuffer();
    onBytes(new Uint8Array(buf), file.name);
  };

  return (
    <div className="dz-wrap">
      <div
        className={'dropzone' + (over ? ' over' : '')}
        onDragOver={(e) => {
          e.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setOver(false);
          const f = e.dataTransfer.files?.[0];
          if (f) void handleFile(f);
        }}
        onClick={() => inputRef.current?.click()}
      >
        <div className="dz-logo">◆ GATF Trace Viewer</div>
        <div className="dz-big">Drop a <code>*-trace.zip</code> here</div>
        <div className="dz-sub muted">or click to choose a file</div>
        <input
          ref={inputRef}
          type="file"
          accept=".zip"
          style={{ display: 'none' }}
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) void handleFile(f);
          }}
        />
      </div>
      {error && <div className="dz-error">{error}</div>}
      <div className="dz-help muted small">
        The bundle is parsed entirely in your browser — nothing is uploaded. It carries network (HAR),
        console, DOM snapshots + screenshots, performance &amp; web-vitals, memory, CPU, coverage and
        video, exactly as recorded by the GATF trace recorders.
      </div>
    </div>
  );
}
