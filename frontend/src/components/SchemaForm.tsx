import { useState } from 'react';
import type { CfgSchema } from '../config/gatfConfigSchema';

// Enum lists resolved by enumVar path (e.g. 'miscMap.providers', 'alltestcasefiles').
export type EnumMap = Record<string, string[]>;

function humanize(key: string): string {
  return key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/^./, (c) => c.toUpperCase());
}

function defaultFor(node: CfgSchema): unknown {
  switch (node.type) {
    case 'object':
      return {};
    case 'array':
      return [];
    case 'map':
      return {};
    case 'boolean':
      return node.default === 'true';
    case 'integer':
    case 'number':
      return null;
    default:
      return '';
  }
}

function Scalar({
  node,
  value,
  onChange,
  enums,
}: {
  node: CfgSchema;
  value: unknown;
  onChange: (v: unknown) => void;
  enums: EnumMap;
}) {
  if (node.type === 'boolean') {
    return (
      <input
        type="checkbox"
        className="sf-check"
        checked={value === true || value === 'true'}
        onChange={(e) => onChange(e.target.checked)}
      />
    );
  }
  if (node.type === 'integer' || node.type === 'number') {
    return (
      <input
        type="number"
        className="sf-input"
        step={node.type === 'integer' ? 1 : 'any'}
        value={value === null || value === undefined ? '' : String(value)}
        onChange={(e) => {
          const t = e.target.value;
          onChange(t === '' ? null : Number(t));
        }}
      />
    );
  }
  // string
  if (node.enumVar) {
    const opts = enums[node.enumVar] ?? [];
    const cur = value == null ? '' : String(value);
    const list = cur && !opts.includes(cur) ? [cur, ...opts] : opts;
    return (
      <select className="sf-input" value={cur} onChange={(e) => onChange(e.target.value || null)}>
        <option value="">—</option>
        {list.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    );
  }
  return (
    <input
      type="text"
      className="sf-input"
      value={value == null ? '' : String(value)}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

function ArrayField({
  node,
  value,
  onChange,
  enums,
}: {
  node: CfgSchema;
  value: unknown;
  onChange: (v: unknown) => void;
  enums: EnumMap;
}) {
  const arr = Array.isArray(value) ? value : [];
  const item = node.items ?? { type: 'string' };
  const complex = item.type === 'object' || item.type === 'array' || item.type === 'map';
  return (
    <div className="sf-array">
      {arr.length === 0 && <div className="sf-empty muted small">none</div>}
      {arr.map((v, i) => (
        <div className={'sf-arr-row' + (complex ? ' sf-arr-complex' : '')} key={i}>
          <div className="sf-arr-body">
            <Field
              node={item}
              value={v}
              onChange={(nv) => {
                const next = arr.slice();
                next[i] = nv;
                onChange(next);
              }}
              enums={enums}
            />
          </div>
          <button
            type="button"
            className="sf-x"
            title="Remove"
            onClick={() => onChange(arr.filter((_, j) => j !== i))}
          >
            ✕
          </button>
        </div>
      ))}
      <button type="button" className="sf-add" onClick={() => onChange([...arr, defaultFor(item)])}>
        + Add
      </button>
    </div>
  );
}

function MapField({
  node,
  value,
  onChange,
  enums,
}: {
  node: CfgSchema;
  value: unknown;
  onChange: (v: unknown) => void;
  enums: EnumMap;
}) {
  // local row state so editing a key doesn't reorder/drop the row mid-edit
  const [rows, setRows] = useState<[string, unknown][]>(() =>
    value && typeof value === 'object' ? Object.entries(value as Record<string, unknown>) : [],
  );
  const valSchema = node.types?.value ?? { type: 'string' };
  const push = (next: [string, unknown][]) => {
    setRows(next);
    const obj: Record<string, unknown> = {};
    for (const [k, v] of next) if (k !== '') obj[k] = v;
    onChange(obj);
  };
  return (
    <div className="sf-map">
      {rows.length === 0 && <div className="sf-empty muted small">none</div>}
      {rows.map(([k, v], i) => (
        <div className="sf-map-row" key={i}>
          <input
            className="sf-input sf-map-key"
            placeholder="key"
            value={k}
            onChange={(e) => {
              const next = rows.slice();
              next[i] = [e.target.value, v];
              push(next);
            }}
          />
          <div className="sf-map-val">
            <Field
              node={valSchema}
              value={v}
              onChange={(nv) => {
                const next = rows.slice();
                next[i] = [k, nv];
                push(next);
              }}
              enums={enums}
            />
          </div>
          <button type="button" className="sf-x" title="Remove" onClick={() => push(rows.filter((_, j) => j !== i))}>
            ✕
          </button>
        </div>
      ))}
      <button type="button" className="sf-add" onClick={() => push([...rows, ['', defaultFor(valSchema)]])}>
        + Add entry
      </button>
    </div>
  );
}

function ObjectField({
  node,
  value,
  onChange,
  enums,
}: {
  node: CfgSchema;
  value: unknown;
  onChange: (v: unknown) => void;
  enums: EnumMap;
}) {
  const obj = (value && typeof value === 'object' ? value : {}) as Record<string, unknown>;
  const props = node.properties ?? {};
  return (
    <div className="sf-obj">
      {Object.entries(props).map(([key, child]) => {
        const isSection = child.label?.type === 'section';
        const scalar =
          child.type === 'string' || child.type === 'integer' || child.type === 'number' || child.type === 'boolean';
        // scalars flow into the responsive column grid; sections and complex fields (object/array/map)
        // span the full width so they keep room to breathe.
        const span = isSection || !scalar;
        return (
          <div className={'sf-field' + (span ? ' sf-span' : '')} key={key}>
            {isSection && <div className="sf-section">{child.label?.value || humanize(key)}</div>}
            <div className={scalar ? 'sf-row' : 'sf-block'}>
              {!child.nolabel && <label className="sf-label">{humanize(key)}</label>}
              <div className="sf-control">
                <Field
                  node={child}
                  value={obj[key]}
                  onChange={(nv) => onChange({ ...obj, [key]: nv })}
                  enums={enums}
                />
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function Field({
  node,
  value,
  onChange,
  enums,
}: {
  node: CfgSchema;
  value: unknown;
  onChange: (v: unknown) => void;
  enums: EnumMap;
}) {
  switch (node.type) {
    case 'object':
      return <ObjectField node={node} value={value} onChange={onChange} enums={enums} />;
    case 'array':
      return <ArrayField node={node} value={value} onChange={onChange} enums={enums} />;
    case 'map':
      return <MapField node={node} value={value} onChange={onChange} enums={enums} />;
    default:
      return <Scalar node={node} value={value} onChange={onChange} enums={enums} />;
  }
}

export default function SchemaForm({
  schema,
  value,
  onChange,
  enums,
}: {
  schema: CfgSchema;
  value: Record<string, unknown>;
  onChange: (v: Record<string, unknown>) => void;
  enums: EnumMap;
}) {
  return (
    <div className="sf">
      <Field node={schema} value={value} onChange={(v) => onChange(v as Record<string, unknown>)} enums={enums} />
    </div>
  );
}
