#!/usr/bin/env python3
"""
Reference importer: Playwright spec -> seleasy. Mirrors com.gatf.imports.PlaywrightImporter's mapping
so the emitted grammar can be verified against tools/smoke/sel-lint.py without a JVM. Best-effort,
line/regex based (no JS AST). Unsupported constructs become // comments AND are reported.
"""
import re, sys, json

def match_brace(s, i):
    depth=0; q=None
    while i < len(s):
        c=s[i]
        if q:
            if c==q and s[i-1]!='\\': q=None
        elif c in '"\'`': q=c
        elif c=='{': depth+=1
        elif c=='}':
            depth-=1
            if depth==0: return i
        i+=1
    return len(s)

def split_args(a):
    out=[]; depth=0; q=None; cur=''
    for i,c in enumerate(a):
        if q:
            cur+=c
            if c==q and a[i-1]!='\\': q=None
            continue
        if c in '"\'`': q=c; cur+=c; continue
        if c in '({[': depth+=1; cur+=c; continue
        if c in ')}]': depth-=1; cur+=c; continue
        if c==',' and depth==0: out.append(cur.strip()); cur=''; continue
        cur+=c
    if cur.strip(): out.append(cur.strip())
    return out

def unq(s):
    s=s.strip()
    if len(s)>=2 and s[0] in '"\'`' and s[-1]==s[0]: return s[1:-1]
    return s

def q(v):
    if '"' in v and "'" not in v: return "'"+v+"'"
    return '"'+v.replace('"','\\"')+'"'

def xp(x):
    x=x.strip()
    if '"' in x and "'" not in x: return "xpath@'"+x+"'"
    return 'xpath@"'+x.replace('"','\\"')+'"'

def pw_selector(raw):
    s=raw.strip()
    if s.startswith('xpath='): return xp(s[6:])
    if s.startswith('//') or s.startswith('(//'): return xp(s)
    if s.startswith('text='): return 'text@'+q(unq(s[5:]))
    if s.startswith('css='): s=s[4:]
    if s.startswith('id='): return 'id@'+q(s[3:])
    if re.fullmatch(r'#[A-Za-z][\w-]*', s): return 'id@'+s[1:]
    return 'css@'+q(s)

def getby_selector(fn, val):
    f=fn.lower()
    if f=='getbytext': return 'text@'+q(val), None
    if f=='getbyrole': return xp("//*[@role='%s' or self::%s]"%(val,val)), ('getByRole approximated')
    if f=='getbylabel': return xp("//label[contains(.,'%s')]//input"%val), 'getByLabel approximated'
    if f=='getbyplaceholder': return xp("//*[@placeholder='%s']"%val), None
    if f=='getbytestid': return xp("//*[@data-testid='%s']"%val), None
    return xp("//*[contains(.,'%s')]"%val), (fn+' approximated')

def esc(v): return v.replace('\\','\\\\').replace('"','\\"')

def emit_action(method, sel, args, unsupported, lineno, stmt):
    a=split_args(args)
    m=method.lower()
    if m=='click': return 'click '+sel
    if m=='dblclick': return 'doubleclick '+sel
    if m=='hover': return 'hover '+sel
    if m in ('check','uncheck'): return 'click '+sel
    if m in ('fill','type'):
        v=unq(a[0]) if a else ''
        return 'type "%s" %s'%(esc(v), sel)
    if m=='press':
        k=unq(a[0]) if a else ''
        return 'sendkeys "%s" %s'%(esc(k), sel)
    if m=='selectoption':
        if not a: return 'select first@0 '+sel
        arg=a[0]
        ml=re.search(r"label\s*:\s*(['\"`])(.*?)\1",arg)
        mi=re.search(r"index\s*:\s*(\d+)",arg)
        mv=re.search(r"value\s*:\s*(['\"`])(.*?)\1",arg)
        if ml: return 'select text@"%s" %s'%(esc(ml.group(2)), sel)
        if mi: return 'select index@%s %s'%(mi.group(1), sel)
        if mv: return 'select value@"%s" %s'%(esc(mv.group(2)), sel)
        return 'select value@"%s" %s'%(esc(unq(arg)), sel)
    if m=='setinputfiles':
        f=unq(a[0]) if a else ''
        return 'upload "%s" %s'%(esc(f), sel)
    if m=='clear': return 'clear '+sel
    unsupported.append((lineno,stmt,"unrecognised action '%s'"%m))
    return None

def emit_expect(subject, matcher, args, unsupported, lineno, stmt):
    a=split_args(args)
    sel=None
    ml=re.search(r"locator\s*\(\s*(['\"`])(.*?)\1",subject)
    if ml: sel=pw_selector(ml.group(2))
    else:
        gm=re.search(r"(getBy[A-Za-z]+)\s*\(\s*(['\"`])(.*?)\2",subject)
        if gm: sel,_=getby_selector(gm.group(1),gm.group(3))
    if matcher in ('tobevisible','tobeattached','tobeenabled'):
        return ('??+ '+sel) if sel else None
    if matcher in ('tohavetext','tocontaintext'):
        t=unq(a[0]) if a else ''
        return '??+ text@"%s"'%esc(t)
    if matcher in ('tobehidden','tobedisabled'):
        return ('??- '+sel) if sel else None
    unsupported.append((lineno,stmt,"assertion '%s' has no direct seleasy equivalent"%matcher))
    return '// UNSUPPORTED assert: '+stmt.strip()[:80]

def args_after(s, open_idx):
    depth=0;q2=None
    for i in range(open_idx,len(s)):
        c=s[i]
        if q2:
            if c==q2 and s[i-1]!='\\': q2=None
        elif c in '"\'`': q2=c
        elif c=='(': depth+=1
        elif c==')':
            depth-=1
            if depth==0: return s[open_idx+1:i]
    return s[open_idx+1:]

def translate_body(body, out, unsupported):
    actions=0
    for raw in re.split(r';\s*\n|\n', body):
        s=raw.strip()
        if not s or s.startswith('//'): continue
        lm=re.search(r"(?:await\s+)?page\s*\.\s*locator\s*\(\s*(['\"`])(.*?)\1\s*\)\s*\.\s*([a-zA-Z]+)\s*\(",s)
        if lm:
            sel=pw_selector(lm.group(2)); method=lm.group(3)
            line=emit_action(method,sel,args_after(s,lm.end()-1),unsupported,0,s)
            if line: out.append('  '+line); actions+=1
            continue
        gm=re.search(r"(?:await\s+)?page\s*\.\s*(getBy[A-Za-z]+)\s*\(\s*(['\"`])(.*?)\2",s)
        if gm:
            sel,note=getby_selector(gm.group(1),gm.group(3))
            if note: unsupported.append((0,s,note))
            act=re.search(r"\.\s*([a-zA-Z]+)\s*\(", s[s.index(')',gm.end()):]) if ')' in s[gm.end():] else None
            if act:
                base=s.index(')',gm.end())
                method=act.group(1)
                line=emit_action(method,sel,args_after(s,base+act.end()-1),unsupported,0,s)
                if line: out.append('  '+line); actions+=1
            continue
        em=re.search(r"(?:await\s+)?expect\s*\(\s*(.*?)\s*\)\s*\.\s*(?:not\.)?([a-zA-Z]+)\s*\(",s)
        if em:
            line=emit_expect(em.group(1),em.group(2).lower(),args_after(s,em.end()-1),unsupported,0,s)
            if line: out.append('  '+line); actions+=1
            continue
        pm=re.search(r"(?:await\s+)?page\s*\.\s*([a-zA-Z]+)\s*\(",s)
        if pm:
            method=pm.group(1).lower(); a=split_args(args_after(s,pm.end()-1))
            if method=='goto':
                if a: out.append('  goto '+unq(a[0])); actions+=1
                continue
            if method=='waitfortimeout':
                ms=int(re.sub(r'[^0-9]','',a[0])) if a and re.sub(r'[^0-9]','',a[0]) else 0
                out.append('  sleep %d'%max(1,round(ms/1000))); actions+=1; continue
            if method in ('waitforselector','waitfor'):
                if a: out.append('  ??+ '+pw_selector(unq(a[0]))); actions+=1
                continue
            if method in ('waitforloadstate','waitfornavigation'):
                out.append('  sleep 1'); actions+=1; continue
            if a:
                sel=pw_selector(unq(a[0])); rest=', '.join(a[1:]) if len(a)>1 else ''
                line=emit_action(method,sel,rest,unsupported,0,s)
                if line: out.append('  '+line); actions+=1; continue
            unsupported.append((0,s,"unrecognised page.%s(...)"%method))
            out.append('  // UNSUPPORTED: '+s[:80])
    return actions

def main():
    src=open(sys.argv[1],encoding='utf-8').read()
    name=sys.argv[2] if len(sys.argv)>2 else 'imported'
    out=["// Imported from Playwright by GATF — best-effort; review selectors and assertions.","open chrome","maximize",""]
    unsupported=[]
    spans=[]
    for m in re.finditer(r"\btest(?:\.(?:only|skip|fixme))?\s*\(\s*(['\"`])(.*?)\1", src, re.S):
        arrow=src.find('=>', m.end())
        bs=src.index('{', arrow if arrow>=0 else m.end())
        be=match_brace(src, bs)
        spans.append((bs+1, be, m.group(2)))
    if not spans: spans=[(0,len(src),name)]
    tests=actions=0
    for bs,be,nm in spans:
        tests+=1
        out.append('subtest "%s"'%nm.replace('"','\\"')); out.append('{')
        actions+=translate_body(src[bs:be], out, unsupported)
        out.append('}'); out.append('')
    sel='\n'.join(out)+'\n'
    outdir=sys.argv[3] if len(sys.argv)>3 else '.'
    import os; os.makedirs(outdir,exist_ok=True)
    p=os.path.join(outdir,name+'.sel')
    open(p,'w',encoding='utf-8').write(sel)
    print("wrote %s (tests=%d actions=%d unsupported=%d)"%(p,tests,actions,len(unsupported)))
    for u in unsupported: print("  UNSUPPORTED L%d: %s :: %s"%(u[0],u[1][:70],u[2]))

if __name__=='__main__': main()
