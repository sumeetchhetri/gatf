/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
// Converts a TextMate grammar (the VS Code extension's seleasy.tmLanguage.json) into a Prism grammar, so the website
// highlights seleasy with the same rules as the editor. Pure: no file access. scripts/prepare.mjs writes the result.
//
// Mapping:
//   - the top-level "patterns" order (of "#include"s) is kept: Prism tries tokens in insertion order, as TextMate does;
//   - a "match" rule becomes one token; "begin"/"end" becomes one token matching BEGIN(INNER|any)*?END, where INNER are
//     the rule's own match patterns (so an escaped quote never ends a string);
//   - the TextMate scope picks the Prism token type that themes colour (comment, string, keyword, ...);
//   - every regex is compiled with the "m" flag (TextMate matches line by line, so ^ and $ are line anchors).
// Oniguruma-only syntax (\\A, \\z, \\G, \\h, possessive or atomic groups) is rejected: better a build error than
// silently different highlighting.

const SCOPE_TO_PRISM = [
  [/^comment\b/, 'comment'],
  [/^string\b/, 'string'],
  [/^constant\.character\.escape\b/, 'char'],
  [/^constant\.numeric\b/, 'number'],
  [/^constant\.language\b/, 'constant'],
  [/^variable\b/, 'variable'],
  [/^entity\.name\.tag\b/, 'tag'],
  [/^support\.function\b/, 'function'],
  [/^keyword\.operator\b/, 'operator'],
  [/^keyword\b/, 'keyword'],
  [/^punctuation\b/, 'punctuation'],
  [/^meta\.embedded\b/, 'embedded'],
];

/** Prism token type for a TextMate scope name, or null when the scope has no colour of its own. */
export function prismType(scope) {
  if (!scope) return null;
  for (const [re, type] of SCOPE_TO_PRISM) if (re.test(scope)) return type;
  return null;
}

const UNSUPPORTED = [/\\[AzZGhH]/, /\(\?>/, /[*+?}]\+/, /\(\?[imx-]+\)/, /\\p\{/];

/**
 * TextMate runs a "match" (and a begin/end delimiter) against one line at a time, so a negated class such as [^"]
 * never crosses a newline there. Prism matches the whole block, so add \\n to every negated class to keep it local.
 */
export function lineLocal(src) {
  return src.replace(/(^|[^\\])((?:\\\\)*)\[\^(?!\\n)/g, '$1$2[^\\n');
}

/** Compiles one Oniguruma source as a JS RegExp, or throws naming the rule. */
export function toRegExp(rawSrc, where) {
  const src = lineLocal(rawSrc);
  for (const re of UNSUPPORTED) {
    if (re.test(rawSrc)) throw new Error(`tm2prism: unsupported Oniguruma syntax in ${where}: ${src}`);
  }
  try {
    return new RegExp(src, 'm');
  } catch (e) {
    throw new Error(`tm2prism: ${where}: ${e.message}`);
  }
}

function captureType(caps) {
  if (!caps) return null;
  for (const k of Object.keys(caps).sort()) {
    const t = prismType(caps[k] && caps[k].name);
    if (t) return t;
  }
  return null;
}

/** One TextMate rule -> [tokenName, prismTokenDef] or null (a rule with nothing to colour). */
function convertRule(rule, key, i) {
  const where = `repository.${key}[${i}]`;
  if (rule.include) throw new Error(`tm2prism: nested include not supported in ${where}`);
  if (rule.match) {
    const alias = prismType(rule.name) || captureType(rule.captures);
    if (!alias) return null;
    return [`${key}-${i}`, { pattern: toRegExp(rule.match, where), alias, greedy: true }];
  }
  if (rule.begin && rule.end) {
    const inner = (rule.patterns || []).filter((p) => p.match).map((p) => lineLocal(p.match));
    toRegExp(rule.begin, where + '.begin');
    toRegExp(rule.end, where + '.end');
    inner.forEach((m, j) => toRegExp(m, `${where}.patterns[${j}]`));
    const body = inner.length ? `(?:${inner.map((m) => `(?:${m})`).join('|')}|[\\s\\S])*?` : '[\\s\\S]*?';
    const def = {
      pattern: toRegExp(`(?:${lineLocal(rule.begin)})${body}(?:${lineLocal(rule.end)})`, where),
      alias: prismType(rule.name) || captureType(rule.beginCaptures) || 'embedded',
      greedy: true,
    };
    const inside = {};
    (rule.patterns || []).forEach((p, j) => {
      const t = p.match && prismType(p.name);
      if (t) inside[`${key}-${i}-${j}`] = { pattern: toRegExp(p.match, `${where}.patterns[${j}]`), alias: t };
    });
    const bt = captureType(rule.beginCaptures);
    if (bt) inside[`${key}-${i}-begin`] = { pattern: toRegExp(rule.begin, where + '.begin'), alias: bt };
    const et = captureType(rule.endCaptures);
    if (et) inside[`${key}-${i}-end`] = { pattern: toRegExp(rule.end, where + '.end'), alias: et };
    if (Object.keys(inside).length) def.inside = inside;
    return [`${key}-${i}`, def];
  }
  throw new Error(`tm2prism: rule without match or begin/end in ${where}`);
}

/** TextMate grammar object -> Prism grammar object (token name -> { pattern, alias, greedy, inside? }). */
export function tmToPrism(tm) {
  const out = {};
  for (const top of tm.patterns || []) {
    if (!top.include || !top.include.startsWith('#')) throw new Error('tm2prism: top-level patterns must be #includes');
    const key = top.include.slice(1);
    const entry = tm.repository && tm.repository[key];
    if (!entry) throw new Error(`tm2prism: missing repository entry ${key}`);
    (entry.patterns || [entry]).forEach((rule, i) => {
      const r = convertRule(rule, key, i);
      if (r) out[r[0]] = r[1];
    });
  }
  return out;
}

function serialise(v, indent) {
  const pad = '  '.repeat(indent);
  if (v instanceof RegExp) return `new RegExp(${JSON.stringify(v.source)}, ${JSON.stringify(v.flags)})`;
  if (v && typeof v === 'object') {
    const body = Object.entries(v).map(([k, x]) => `${pad}  ${JSON.stringify(k)}: ${serialise(x, indent + 1)}`);
    return `{\n${body.join(',\n')}\n${pad}}`;
  }
  return JSON.stringify(v);
}

/** The generated ES module (src/prism/seleasy.generated.js). */
export function emitModule(prism, sourceName, header) {
  return `${header}// GENERATED by website/scripts/prepare.mjs from ${sourceName}. Do not edit; it is rebuilt on every start/build.
const seleasy = ${serialise(prism, 0)};
export default seleasy;
`;
}
