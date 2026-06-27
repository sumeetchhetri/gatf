import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { executorSchema } from '../config/gatfConfigSchema';
import SchemaForm, { type EnumMap } from '../components/SchemaForm';

function asStrArr(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v
    .map((x) =>
      typeof x === 'string'
        ? x
        : x && typeof x === 'object' && 'name' in (x as object)
          ? String((x as { name: unknown }).name)
          : String(x),
    )
    .filter((s) => s !== '');
}

export default function ConfigView() {
  const [cfg, setCfg] = useState<Record<string, unknown> | null>(null);
  const [original, setOriginal] = useState<Record<string, unknown> | null>(null);
  const [enums, setEnums] = useState<EnumMap>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [ver, setVer] = useState(0); // bump to remount the form (resets map-row local state)
  const [rawOpen, setRawOpen] = useState(false);
  const [rawText, setRawText] = useState('');
  const [rawErr, setRawErr] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([
      api.configExecutor(),
      api.misc().catch(() => ({}) as Record<string, unknown>),
      api.testfiles().catch(() => null),
    ])
      .then(([config, misc, tf]) => {
        setEnums({
          'miscMap.providers': asStrArr(misc.providers),
          'miscMap.datasourcecls': asStrArr(misc.datasourcecls),
          'miscMap.datasources': asStrArr(misc.datasources),
          'miscMap.providercls': asStrArr(misc.providercls),
          alltestcasefiles: tf ? tf.files.map((f) => f.path) : [],
        });
        setCfg(config);
        setOriginal(config);
        setVer((v) => v + 1);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const dirty = useMemo(
    () => cfg != null && original != null && JSON.stringify(cfg) !== JSON.stringify(original),
    [cfg, original],
  );

  const onSave = () => {
    if (!cfg) return;
    setSaving(true);
    setSaveErr(null);
    setSaved(false);
    api
      .saveConfigExecutor(cfg)
      .then((savedCfg) => {
        const next = savedCfg && typeof savedCfg === 'object' ? savedCfg : cfg;
        setOriginal(next);
        setCfg(next);
        setVer((v) => v + 1);
        setSaved(true);
        setTimeout(() => setSaved(false), 4000);
      })
      .catch((e) => setSaveErr(e.message))
      .finally(() => setSaving(false));
  };

  const reset = () => {
    setCfg(original);
    setSaveErr(null);
    setVer((v) => v + 1);
  };

  const openRaw = () => {
    setRawText(JSON.stringify(cfg ?? {}, null, 2));
    setRawErr(null);
    setRawOpen(true);
  };
  const applyRaw = () => {
    try {
      const parsed = JSON.parse(rawText);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed))
        throw new Error('Config must be a JSON object');
      setCfg(parsed as Record<string, unknown>);
      setVer((v) => v + 1);
      setRawOpen(false);
      setRawErr(null);
    } catch (e) {
      setRawErr((e as Error).message);
    }
  };

  return (
    <div className="view">
      <div className="view-head">
        <h2>Configuration</h2>
        <span className="muted small">executor</span>
      </div>

      {loading && <div className="view-note">Loading…</div>}
      {error && (
        <div className="view-error">
          Could not load the config ({error}). Rebuild the JAR from this delta and make sure you can reach{' '}
          <code>/configure</code>.
        </div>
      )}

      {cfg && !loading && (
        <>
          <div className="cfg-bar">
            <button className="btn primary" disabled={!dirty || saving} onClick={onSave}>
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button className="btn ghost" disabled={!dirty || saving} onClick={reset}>
              Reset
            </button>
            <button className="btn ghost small" onClick={rawOpen ? () => setRawOpen(false) : openRaw}>
              {rawOpen ? 'Close JSON' : 'Edit as JSON'}
            </button>
            {dirty && !saving && <span className="muted small">unsaved changes</span>}
            {saved && <span className="cfg-ok small">saved ✓</span>}
          </div>

          <div className="view-note muted small">
            Saving writes <code>gatf-config.json</code> and re-initialises the executor context — the same as the
            classic UI's save.
          </div>

          {saveErr && <div className="view-error">Save failed: {saveErr}</div>}

          {rawOpen ? (
            <section className="view-section">
              <textarea
                className="cfg-raw mono"
                spellCheck={false}
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
              />
              {rawErr && <div className="view-error">{rawErr}</div>}
              <div className="cfg-bar">
                <button className="btn primary small" onClick={applyRaw}>
                  Apply JSON
                </button>
                <span className="muted small">Applies to the form; use Save to persist.</span>
              </div>
            </section>
          ) : (
            <section className="view-section">
              <SchemaForm key={ver} schema={executorSchema} value={cfg} onChange={setCfg} enums={enums} />
            </section>
          )}
        </>
      )}
    </div>
  );
}
