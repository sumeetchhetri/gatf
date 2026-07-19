import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { executorSchema, generatorSchema } from '../config/gatfConfigSchema';
import SchemaForm, { type EnumMap } from '../components/SchemaForm';

type CfgKind = 'executor' | 'generator';

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
  const [kind, setKind] = useState<CfgKind>('executor');
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
    setSaveErr(null);
    setSaved(false);
    if (kind === 'generator') {
      api
        .configByType('generator')
        .then((config) => {
          setEnums({});
          setCfg(config);
          setOriginal(config);
          setVer((v) => v + 1);
        })
        .catch((e) => setError(e.message))
        .finally(() => setLoading(false));
      return;
    }
    Promise.all([
      api.configByType('executor'),
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

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [kind]);

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
      .saveConfig(kind, cfg)
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
        <div className="view-head-right">
          <button
            className={'btn small' + (kind === 'executor' ? ' primary' : ' ghost')}
            disabled={dirty && kind !== 'executor'}
            title={dirty && kind !== 'executor' ? 'Save or reset first' : ''}
            onClick={() => setKind('executor')}
          >
            Executor
          </button>
          <button
            className={'btn small' + (kind === 'generator' ? ' primary' : ' ghost')}
            disabled={dirty && kind !== 'generator'}
            title={dirty && kind !== 'generator' ? 'Save or reset first' : ''}
            onClick={() => setKind('generator')}
          >
            Generator
          </button>
        </div>
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
            {kind === 'executor'
              ? 'Saving writes gatf-config.json and re-initialises the executor context — the same as the classic UI\u2019s save.'
              : 'Saving writes the generator configuration (gatf-generator.json) — the same as the classic UI\u2019s Generator Configuration save.'}
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
              <SchemaForm
                key={ver}
                schema={kind === 'executor' ? executorSchema : generatorSchema}
                value={cfg}
                onChange={setCfg}
                enums={enums}
              />
            </section>
          )}
        </>
      )}
    </div>
  );
}
