import type { TestCaseReport } from '../api';
import { fmtMs } from '../util';

export function apiStatusClass(s: string): string {
  const u = (s || '').toLowerCase();
  if (u === 'success' || u === 'pass' || u === 'passed') return 'st-pass';
  if (u === 'skipped' || u === 'skip') return 'st-warn';
  return 'st-fail';
}

// Renders one API test-case result: status line (optional), error, cURL, request/response, server logs.
// Shared by the report viewer (ApiReportView) and the Play result panel in the test-case editor, so the
// two show identical detail. The Play/Replay controls are supplied by the caller, not this component.
export default function ApiResult({ r, showStatus }: { r: TestCaseReport; showStatus?: boolean }) {
  const reqHdr = r.requestHeaders;
  const resHdr = r.responseHeaders || r.resHeaders;
  const time = r.averageExecutionTime ?? r.executionTime;
  return (
    <div className="api-result">
      {showStatus && (
        <div className="api-result-status">
          <span className={'st-badge ' + apiStatusClass(r.status)}>{r.status || '—'}</span>
          {r.method && <span className="mono muted small">{r.method}</span>}
          {(r.actualUrl || r.url) && (
            <span className="mono small ellipsis" style={{ maxWidth: 360 }} title={r.actualUrl || r.url}>
              {r.actualUrl || r.url}
            </span>
          )}
          {r.responseStatusCode != null && <span className="mono small">code {r.responseStatusCode}</span>}
          {time != null && <span className="mono muted small">{fmtMs(time)}</span>}
        </div>
      )}
      {(r.error || r.errorText || r.failureReason) && (
        <div className="view-error" style={{ margin: '0 0 10px' }}>
          {r.failureReason || r.error || r.errorText}
        </div>
      )}
      {r.curlCmd && (
        <>
          <div className="hdr-title">cURL</div>
          <pre className="body-pre">{r.curlCmd}</pre>
        </>
      )}
      {reqHdr && (
        <>
          <div className="hdr-title">Request headers</div>
          <pre className="body-pre">{reqHdr}</pre>
        </>
      )}
      {r.requestContent && (
        <>
          <div className="hdr-title">Request body {r.requestContentType ? `(${r.requestContentType})` : ''}</div>
          <pre className="body-pre">{r.requestContent}</pre>
        </>
      )}
      {resHdr && (
        <>
          <div className="hdr-title">Response headers</div>
          <pre className="body-pre">{resHdr}</pre>
        </>
      )}
      {r.responseContent && (
        <>
          <div className="hdr-title">Response body {r.responseContentType ? `(${r.responseContentType})` : ''}</div>
          <pre className="body-pre">{r.responseContent}</pre>
        </>
      )}
      {r.serverLogs && (
        <>
          <div className="hdr-title">Server logs</div>
          <pre className="body-pre">{r.serverLogs}</pre>
        </>
      )}
    </div>
  );
}
