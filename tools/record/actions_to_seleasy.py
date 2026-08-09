#!/usr/bin/env python3
"""
Reference for record mode's action->seleasy conversion. Mirrors com.gatf.record.RecordSession's mapping
(selector formatting + action verbs) so the output can be verified lint-clean against
tools/smoke/sel-lint.py without a live browser. Input: a JSON array of captured actions in the exact
shape the injected recorder emits: {type, sel:{s,v}, value?, url?}.
"""
import sys, json, re

def q(v):
    if '"' in v and "'" not in v: return "'"+v+"'"
    return '"'+v.replace('"','\\"')+'"'

def simple_or_quoted(v):
    return v if re.fullmatch(r'[A-Za-z0-9_\-:.]+', v) else q(v)

def selector(sel):
    if not sel: return None
    s=sel.get('s'); v=sel.get('v') or ''
    if not v: return None
    if s=='id': return 'id@'+simple_or_quoted(v)
    if s=='name': return 'name@'+simple_or_quoted(v)
    if s=='css': return 'css@'+q(v)
    return 'xpath@'+q(v)

def esc(v): return (v or '').replace('\\','\\\\').replace('"','\\"').replace('\n',' ').replace('\r',' ')

def map_action(a):
    t=a.get('type')
    if t=='goto':
        u=a.get('url') or ''
        return None if (not u or u=='about:blank') else 'goto '+u
    sel=selector(a.get('sel'))
    if not sel: return None
    if t=='click': return 'click '+sel
    if t=='submit': return 'submit '+sel
    if t=='check': return 'click '+sel
    if t=='type': return 'type "%s" %s'%(esc(a.get('value')), sel)
    if t=='select':
        v=a.get('value') or ''
        return ('select index@0 '+sel) if not v else 'select value@"%s" %s'%(esc(v), sel)
    return None

def main():
    actions=json.load(open(sys.argv[1],encoding='utf-8'))
    out=["// Recorded with GATF record mode — review selectors and add assertions.","open chrome","maximize",""]
    last=None
    for a in actions:
        line=map_action(a)
        if line is None or line==last: continue
        out.append(line); last=line
    sel='\n'.join(out)+'\n'
    import os
    outdir=sys.argv[3] if len(sys.argv)>3 else '.'; os.makedirs(outdir,exist_ok=True)
    name=sys.argv[2] if len(sys.argv)>2 else 'recorded'
    p=os.path.join(outdir,name+'.sel'); open(p,'w',encoding='utf-8').write(sel)
    print("wrote %s (%d actions -> %d lines)"%(p,len(actions),len([l for l in out if l and not l.startswith('//') and l not in('open chrome','maximize')])))

if __name__=='__main__': main()
