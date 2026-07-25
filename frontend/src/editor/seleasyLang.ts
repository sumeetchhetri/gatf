// Seleasy language for CodeMirror 6, ported from the classic CodeMirror 5 mode
// (alldep-jar/src/main/resources/gatf-config-tool/codemirror/mode/seleasy/seleasy.js, text/x-seleasy).
// The tokenizer logic is transcribed verbatim; only the wrapper (StreamLanguage + tag table) is new.
import { StreamLanguage, type StringStream, HighlightStyle } from '@codemirror/language';
import { tags as t } from '@lezer/highlight';

// keyword -> style code: 1 keyword, 2 def, 3 atom, 4 operator, 5 variable-2; [x,y] => x is the code.
type Style = number | number[];
export const SELEASY: Record<string, Style> = {
  '->': 4, '++': 4, '--': 4, '**': 4, '=~': 4, '!~': 4, '*': 4, '/': 4, '%': 4, x: 4, '+': 4, '.': 4,
  '<<': 4, '>>': 4, '<': 4, '>': 4, '<=': 4, '>=': 4, lt: 4, gt: 4, le: 4, ge: 4, '==': 4, '!=': 4,
  '<=>': 4, eq: 4, ne: 4, cmp: 4, '~~': 4, '&': 4, '|': 4, '^': 4, '&&': 4, '||': 4, '//': 4, '..': 4,
  '...': 4, '??': 1, '??-': 1, '??+': 1, ':?': 1, '?': 4, ':': 4, '=': 4, '+=': 4, '-=': 4, '*=': 4,
  ',': 4, '=>': 4, '::': 4, not: 4, and: 4, or: 4, xor: 4,
  if: [1, 1], elsif: [1, 1], else: [1, 1], while: [1, 1], unless: [1, 1], for: [1, 1], break: 1,
  close: 1, continue: [1, 1], eval: 1, exec: 1, exit: 1, goto: 1, import: 1, index: 1, int: 1, join: 1,
  open: 1, require: 1, sleep: 1, substr: 1, subtest: 1, '@call': 1, '@print': 3, '@driver': 3,
  '@window': 3, '@element': 3, '@sc': 3, '@printprovjson': 3, '@printProv': 3, '@index': 3, '@line': 3,
  '@cntxtparam': 3, pass: 1, fail: 1, warn: 1, dynprops: 1, maximize: 1, include: 1, config: 1, xpath: 3,
  css: 3, id: 3, name: 3, class: 3, tag: 3, cssselector: 3, text: 3, partiallinktext: 3, linktext: 3,
  jq: 3, jquery: 3, active: 3, this: 3, current: 3, var: 1, robot: 1, type: 3, click: 3, back: 1,
  forward: 1, refresh: 1, clear: 3, submit: 3, setting: 1, window: 1, frame: 1, tab: 1, jsvar: 1,
  execjs: 1, screenshot: 1, 'ele-screenshot': 1, hoverclick: 3, actions: 1, chord: 3, provider: 1,
  'transient-provider': 1, confirm: 1, alert: 1, moveby: 3, doubleclick: 3, dblclick: 3, netapix: 1,
  wopensave: 1, sendKeys: 3, moveto: 3, clickhold: 3, release: 3, keyup: 3, keydown: 3, api: 1,
  plugin: 1, jsonwrite: 3, xmlwrite: 3, zoom: 1, pinch: 1, tap: 1, swipe: 1, rotate: 1, hidekeypad: 1,
  shake: 1, chrome: 3, 'chrome-dkr': 3, 'chrome-hdl': 3, 'chrome-rec': 3, firefox: 3, opera: 3,
  safari: 3, ie: 3, edge: 3, readfile: 1, mode: 1, hover: 3, upload: 3, randomize: 3, func: 1,
  relative: 3, sql: 1, dsq: 1, file: 1, curl: 1, scroll: 1, ifnot: 1, loop: 1, over: 1, till: 1,
  visible: 3, attr: 3, fuzzyn: 3, fuzzya: 3, fuzzyauc: 3, fuzzyalc: 3, fuzzyan: 3, fuzzyanuc: 3,
  fuzzyanlc: 3, title: 3, selected: 3, above: 3, below: 3, leftof: 3, rightof: 3, near: 3, true: 3,
  false: 3, filevar: 1, others: 3, on: 3, off: 3, post: 3, get: 3, put: 3, delete: 3, tagname: 3,
  width: 3, height: 3, posx: 3, posy: 3, main: 3, cssvalue: 3, lazy: 3, jsonread: 3, jsonpath: 3,
  xmlread: 3, xmlpath: 3, mongo: 1, counter: 1, ds: 1, query: 1, timeout: 1, waitready: 1, scrollup: 1,
  scrollpageup: 1, scrollpagedown: 1, scrolldown: 1, execjsfile: 1, canvas: 1, touch: 1, variable: 1,
  layer: 3, normal: 3, integration: 3, parent: 3, clk_focus: 3, capability_set: 1, currenturl: 3,
  pagesource: 3, xpos: 3, ypos: 3, alerttext: 3, alphanumeric: 3, alpha: 3, alphanumericlc: 3,
  alphalc: 3, alphanumericuc: 3, alphauc: 3, numeric: 3, value: 3, range: 3, prefixed: 3, prefixed_: 3,
  status: 3, header: 3, json: 3, select: 3, clickandhold: 3, contextclick: 3, rightclick: 3,
  movetoelement: 3, draganddrop: 3, dragdrop: 3, dragdrop1: 3, draganddrop1: 3, movebyoffset: 3, ok: 3,
  yes: 3, cancel: 3, no: 3, enabled: 3, className: 3,
};

interface SelState {
  tokenize: ((s: StringStream, st: SelState) => string | null) | null;
  chain: string[] | string | null;
  style: string | null;
  tail: string | RegExp | null;
}

// module-level, like the original mode
let _type: string | null = null;
let _content: string | undefined;
function ret(tp: string, style: string, cont?: string): string {
  _type = tp;
  _content = cont;
  void _type;
  void _content;
  return style;
}
function look(stream: StringStream, c?: number): string {
  return stream.string.charAt(stream.pos + (c || 0));
}
function prefix(stream: StringStream, c?: number): string {
  if (c) {
    const x = stream.pos - c;
    return stream.string.substr(x >= 0 ? x : 0, c);
  }
  return stream.string.substr(0, stream.pos - 1);
}
function suffix(stream: StringStream, c?: number): string {
  const y = stream.string.length;
  const x = y - stream.pos + 1;
  return stream.string.substr(stream.pos, c && c < y ? c : x);
}

function tokenComment(stream: StringStream, state: SelState): string {
  let maybeEnd = false,
    ch: string | void;
  while ((ch = stream.next())) {
    if (ch === '/' && maybeEnd) {
      state.tokenize = tokenSeleasy;
      break;
    }
    maybeEnd = ch === '*';
  }
  return ret('comment', 'comment');
}

function tokenChain(
  stream: StringStream,
  state: SelState,
  chain: string[] | string,
  style: string,
  tail?: string | RegExp,
): string {
  state.chain = null;
  state.style = null;
  state.tail = null;
  state.tokenize = function (stream: StringStream, state: SelState) {
    let e = false,
      c: string | void,
      i = 0;
    while ((c = stream.next())) {
      if (c === chain[i] && !e) {
        if (chain[++i] !== undefined) {
          state.chain = chain[i];
          state.style = style;
          state.tail = tail || null;
        } else if (tail) stream.eatWhile(tail);
        state.tokenize = tokenSeleasy;
        return style;
      }
      e = !e && c === '\\';
    }
    return style;
  };
  return state.tokenize(stream, state) as string;
}

function tokenSOMETHING(stream: StringStream, state: SelState, string: string): string {
  state.tokenize = function (stream: StringStream, state: SelState) {
    if (stream.string === string) state.tokenize = tokenSeleasy;
    stream.skipToEnd();
    return 'string';
  };
  return state.tokenize(stream, state) as string;
}

// literal keyword prefixes that consume to the next space (or EOL)
const TO_SPACE_KW = ['ele-screenshot', 'browser-scope', 'session-scope', '??-', '??+', '??', '?!', ':?!', ':?', '#'];
// literal keyword prefixes that consume a fixed number of chars
const FIXED_KW: [string, number][] = [
  ['<<<(java)', 9], ['<<<(js)', 7], ['<<<(python)', 11], ['<<<(ruby)', 9], ['<<<(groovy)', 11], ['<<<', 3],
  ['begin code in java', 18], ['begin code in js', 16], ['begin code in groovy', 20], ['begin code in python', 20],
  ['begin code in ruby', 18], ['end code', 8], ['wait for', 8], ['wait till visible', 17], ['>>>', 3],
];

function tokenSeleasy(stream: StringStream, state: SelState): string | null {
  if (stream.eatSpace()) return null;
  const rest = stream.string.substring(stream.start);
  if (rest.startsWith('{')) {
    stream.pos = stream.start + 1;
    return 'operator';
  }
  if (rest.startsWith('}')) {
    stream.pos = stream.start + 1;
    return 'operator';
  }
  if (rest.startsWith('#j')) {
    stream.pos = stream.start + 2;
    return 'keyword';
  }
  if (rest.startsWith('chrome-')) {
    const sp = rest.indexOf(' ');
    stream.pos = sp !== -1 ? stream.start + sp : stream.string.length;
    return 'atom';
  }
  for (const [kw, n] of FIXED_KW) {
    if (rest.startsWith(kw)) {
      stream.pos = stream.start + n;
      return 'keyword';
    }
  }
  for (const kw of TO_SPACE_KW) {
    if (rest.startsWith(kw)) {
      const sp = rest.indexOf(' ');
      stream.pos = stream.start + (sp !== -1 ? sp : rest.length);
      return 'keyword';
    }
  }
  if (state.chain) return tokenChain(stream, state, state.chain, state.style as string, state.tail || undefined);
  if (stream.match(/^(-?((\d[\d_]*)?\.\d+(e[+-]?\d+)?|\d+\.\d*)|0x[\da-fA-F_]+|0b[01_]+|\d[\d_]*(e[+-]?\d+)?)/))
    return 'number';
  if (stream.match(/^<<(?=[_a-zA-Z])/)) {
    stream.eatWhile(/\w/);
    return tokenSOMETHING(stream, state, stream.current().substr(2));
  }
  if (stream.sol() && stream.match(/^=item(?!\w)/)) return tokenSOMETHING(stream, state, '=cut');
  const ch = stream.next();
  if (ch === '"' || ch === "'") {
    if (prefix(stream, 3) === '<<' + ch) {
      const p = stream.pos;
      stream.eatWhile(/\w/);
      const n = stream.current().substr(1);
      if (n && stream.eat(ch)) return tokenSOMETHING(stream, state, n);
      stream.pos = p;
    }
    return tokenChain(stream, state, [ch], 'string');
  }
  if (ch === '`') return tokenChain(stream, state, [ch], 'variable-2');
  if (ch === '/' && stream.string.substring(stream.start).startsWith('/*')) {
    stream.pos = 1;
    if (stream.eat('*')) {
      state.tokenize = tokenComment;
      return tokenComment(stream, state);
    } else if (stream.eat('/')) {
      stream.skipToEnd();
      return ret('comment', 'comment');
    }
  }
  if (ch === '/' && stream.string.substring(stream.start).startsWith('//')) {
    // Phase 7: a `//` is a comment only at line start or when whitespace-preceded, so a
    // trailing `config foo //note` highlights as a comment while `goto https://x` does not
    // (its `//` follows a `:`). Otherwise fall through to operator handling below.
    const before = stream.start === 0 ? ' ' : stream.string.charAt(stream.start - 1);
    if (stream.start === 0 || before === ' ' || before === '\t') {
      stream.skipToEnd();
      return 'comment';
    }
  }
  if (ch === '$') {
    const v = stream.string.substring(stream.start).match(/\$\{[a-zA-Z_0-9]+\}/);
    if (v && v.length > 0 && v[0]) {
      stream.pos += v[0].length - 1;
      return 'variable-2';
    }
    return 'atom';
  }
  if (ch && /[@%]/.test(ch)) {
    const p = stream.pos;
    if (
      (stream.eat('^') && stream.eat(/[A-Z]/)) ||
      (!/[@$%&]/.test(look(stream, -2)) &&
        stream.eat(/[=|\\\-#?@;:&`~^![\]*'"$+.,/<>()]/))
    ) {
      const c = stream.current();
      if (SELEASY[c.toLowerCase()]) return 'variable-2';
    }
    stream.pos = p;
  }
  if (ch && /[:+\-^*$&%@=<>!?|/~.]/.test(ch)) {
    const p = stream.pos;
    stream.eatWhile(/[:+\-^*$&%@=<>!?|/~.]/);
    if (SELEASY[stream.current().toLowerCase()]) return 'operator';
    else stream.pos = p;
  }
  if (ch === '_') {
    if (stream.pos === 1) {
      if (suffix(stream, 6) === '_END__') return tokenChain(stream, state, ['\0'], 'comment');
      else if (suffix(stream, 7) === '_DATA__') return tokenChain(stream, state, ['\0'], 'variable-2');
      else if (suffix(stream, 7) === '_C__') return tokenChain(stream, state, ['\0'], 'string');
    }
  }
  if (ch && /\w/.test(ch)) {
    const p = stream.pos;
    if (look(stream, -2) === '{' && (look(stream, 0) === '}' || (stream.eatWhile(/\w/) && look(stream, 0) === '}')))
      return 'string';
    else stream.pos = p;
  }
  if (ch === '@') {
    const p = stream.pos;
    stream.eatWhile(/[a-zA-Z_]/);
    if (/[\da-z]/.test(look(stream, 0))) {
      stream.pos = p;
    } else {
      let c = SELEASY[stream.current().toLowerCase()];
      if (!c) return 'string';
      if (Array.isArray(c)) c = c[0];
      return styleFor(c as number);
    }
  }
  if (ch && /[a-zA-Z]/.test(ch)) {
    const l = look(stream, -2);
    const p = stream.pos;
    stream.eatWhile(/[a-zA-Z_]/);
    if (/[\da-z]/.test(look(stream, 0))) {
      stream.pos = p;
    } else {
      let c = SELEASY[stream.current().toLowerCase()];
      if (!c) return 'string';
      if (Array.isArray(c)) c = c[0];
      if (l !== ':') return styleFor(c as number, true);
      else return 'string';
    }
  }
  if (ch && /[a-zA-Z_]/.test(ch)) {
    const l = look(stream, -2);
    stream.eatWhile(/\w/);
    let c = SELEASY[stream.current().toLowerCase()];
    if (!c) return 'string';
    if (Array.isArray(c)) c = c[0];
    if (l !== ':') return styleFor(c as number, true);
    else return 'string';
  }
  return 'string';
}

function styleFor(c: number, meta = false): string {
  if (c === 1) return 'keyword';
  if (c === 2) return 'def';
  if (c === 3) return 'atom';
  if (c === 4) return 'operator';
  if (c === 5) return 'variable-2';
  return meta ? 'meta' : 'string';
}

export const seleasyLanguage = StreamLanguage.define<SelState>({
  name: 'seleasy',
  startState: () => ({ tokenize: tokenSeleasy, chain: null, style: null, tail: null }),
  token: (stream, state) => (state.tokenize || tokenSeleasy)(stream, state),
  languageData: {
    commentTokens: { line: '//', block: { open: '/*', close: '*/' } },
    closeBrackets: { brackets: ['(', '[', '{', "'", '"'] },
  },
  tokenTable: {
    keyword: t.keyword,
    def: t.definitionKeyword,
    atom: t.atom,
    operator: t.operator,
    number: t.number,
    string: t.string,
    comment: t.comment,
    meta: t.meta,
    'variable-2': t.special(t.variableName),
    'string-2': t.special(t.string),
  },
});

// Colors reference CSS vars (declared in styles.css) so the editor tracks the app light/dark theme.
export const seleasyHighlight = HighlightStyle.define([
  { tag: t.keyword, color: 'var(--cm-keyword)' },
  { tag: t.definitionKeyword, color: 'var(--cm-keyword)', fontWeight: '600' },
  { tag: t.atom, color: 'var(--cm-atom)' },
  { tag: t.operator, color: 'var(--cm-operator)' },
  { tag: t.number, color: 'var(--cm-number)' },
  { tag: t.string, color: 'var(--cm-string)' },
  { tag: t.special(t.string), color: 'var(--cm-string)' },
  { tag: t.comment, color: 'var(--cm-comment)', fontStyle: 'italic' },
  { tag: t.meta, color: 'var(--cm-meta)' },
  { tag: t.special(t.variableName), color: 'var(--cm-var)' },
]);
