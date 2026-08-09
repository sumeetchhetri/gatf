#!/usr/bin/env python3
"""
Reference importer: JMeter .jmx -> GATF API test cases. Mirrors com.gatf.imports.JMeterImporter's
FIELD MAPPING so it can be verified without a JVM. Emits a representative <TestCases> XML for a
well-formedness check and prints the extracted TestCase fields for inspection.

NOTE: the Java importer builds TestCase objects and serializes them with GATF's own XmlMapper
(WorkflowContextHandler.XOM) — the exact mapper GATF uses to read/write test XML — so the real
output round-trips by construction. This reference validates the mapping, not the serializer.
"""
import sys, json
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

def prop(el, name):
    for tag in ('stringProp','boolProp','intProp','longProp'):
        for c in el.findall(tag):
            if c.get('name')==name: return (c.text or '')
    return None

def children(el, tag): return [c for c in list(el) if c.tag==tag]
def first(el, tag):
    l=children(el,tag); return l[0] if l else None

def following_hashtree(parent, el):
    kids=list(parent); 
    try: idx=kids.index(el)
    except ValueError: return None
    for k in kids[idx+1:]:
        if k.tag=='hashTree': return k
        # only the immediately-following sibling counts
        return None if k.tag!='hashTree' else k
    return None

def elementprop_by_name(el, name):
    for e in children(el,'elementProp'):
        if e.get('name')==name: return e
    return None

def collection_by_name(el, name):
    for e in children(el,'collectionProp'):
        if e.get('name')==name: return e
    return None

def extract_body(sampler):
    raw = (prop(sampler,'HTTPSampler.postBodyRaw')=='true')
    args=elementprop_by_name(sampler,'HTTPsampler.Arguments')
    if args is None: return None
    coll=first(args,'collectionProp')
    if coll is None: return None
    argels=children(coll,'elementProp')
    if raw:
        for a in argels:
            v=prop(a,'Argument.value')
            if v is not None: return v
        return None
    parts=[]
    for a in argels:
        n=prop(a,'Argument.name'); v=prop(a,'Argument.value')
        if n is None: continue
        parts.append('%s=%s'%(n, v or ''))
    return '&'.join(parts) if parts else None

def build_cases(root):
    cases=[]; unsupported=[]; seq=0
    parent_map={c:p for p in root.iter() for c in p}
    # advisory: user-defined variables
    udv=0
    for args in root.iter('Arguments'):
        coll=first(args,'collectionProp')
        if coll is not None: udv+=len(children(coll,'elementProp'))
    if udv: unsupported.append((0,'%d user-defined variable(s)'%udv,'map to gatf-config globalVariables; ${var} refs kept'))
    for tag in ['JSR223Sampler','BeanShellSampler','IfController','LoopController','CSVDataSet','ConstantTimer','UniformRandomTimer']:
        n=len(list(root.iter(tag)))
        if n: unsupported.append((0,'%s x%d'%(tag,n),'no direct GATF equivalent'))

    for sampler in root.iter('HTTPSamplerProxy'):
        seq+=1
        tc={'name':sampler.get('testname') or 'Request %d'%seq,'secure':False}
        protocol=prop(sampler,'HTTPSampler.protocol') or ''
        domain=prop(sampler,'HTTPSampler.domain') or ''
        port=prop(sampler,'HTTPSampler.port') or ''
        path=prop(sampler,'HTTPSampler.path') or ''
        method=(prop(sampler,'HTTPSampler.method') or 'GET').upper()
        tc['method']=method
        if domain:
            scheme=protocol or 'https'
            base=scheme+'://'+domain+('' if port in ('','80','443') else ':'+port)
            tc['baseUrl']=base; tc['url']=path or '/'
        else:
            tc['url']=path or '/'
        body=extract_body(sampler)
        if body: tc['content']=body
        headers={}; nodes=[]; caps={}
        cfg=following_hashtree(parent_map.get(sampler), sampler)
        if cfg is not None:
            for hm in children(cfg,'HeaderManager'):
                coll=first(hm,'collectionProp')
                if coll is None: continue
                for e in children(coll,'elementProp'):
                    nm=prop(e,'Header.name'); val=prop(e,'Header.value')
                    if nm: headers[nm]=val or ''
            for ra in children(cfg,'ResponseAssertion'):
                field=prop(ra,'Assertion.test_field') or ''
                strings=collection_by_name(ra,'Asserion.test_strings')
                if strings is None: strings=collection_by_name(ra,'Assertion.test_strings')
                vals=[ (sp.text or '') for sp in strings.findall('stringProp')] if strings is not None else []
                if 'response_code' in field:
                    for v in vals:
                        try: tc['expectedResCode']=int(v.strip())
                        except: pass
                else:
                    nodes+=[v for v in vals if v]
            for jp in children(cfg,'JSONPostProcessor'):
                names=(prop(jp,'JSONPostProcessor.referenceNames') or '').split(';')
                exprs=(prop(jp,'JSONPostProcessor.jsonPathExprs') or '').split(';')
                for i,nm in enumerate(names):
                    if nm.strip(): caps[nm.strip()]=exprs[i].strip() if i<len(exprs) else ''
            for re_ in children(cfg,'RegexExtractor'):
                ref=prop(re_,'RegexExtractor.refname'); rex=prop(re_,'RegexExtractor.regex')
                if ref and ref.strip(): caps[ref.strip()]=rex or ''
        if headers: tc['headers']=headers
        if nodes: tc['expectedNodes']=nodes
        if caps: tc['workflowContextParameterMap']=caps
        tc['sequence']=seq
        cases.append(tc)
    return cases, unsupported

def to_xml(cases):
    def esc(v): return escape(str(v))
    out=['<TestCases>']
    for tc in cases:
        out.append('  <TestCase>')
        if tc.get('baseUrl'): out.append('    <baseUrl>%s</baseUrl>'%esc(tc['baseUrl']))
        out.append('    <url>%s</url>'%esc(tc['url']))
        out.append('    <name>%s</name>'%esc(tc['name']))
        out.append('    <method>%s</method>'%esc(tc['method']))
        out.append('    <secure>false</secure>')
        if tc.get('headers'):
            out.append('    <headers>')
            for k,v in tc['headers'].items():
                out.append('      <%s><![CDATA[%s]]></%s>'%(k,v,k))
            out.append('    </headers>')
        if tc.get('content'): out.append('    <content><![CDATA[%s]]></content>'%tc['content'])
        if tc.get('expectedResCode'): out.append('    <expectedResCode>%d</expectedResCode>'%tc['expectedResCode'])
        if tc.get('expectedNodes'):
            out.append('    <expectedNodes>')
            for nval in tc['expectedNodes']: out.append('      <string>%s</string>'%esc(nval))
            out.append('    </expectedNodes>')
        if tc.get('workflowContextParameterMap'):
            out.append('    <workflowContextParameterMap>')
            for k,v in tc['workflowContextParameterMap'].items():
                out.append('      <%s><![CDATA[%s]]></%s>'%(k,v,k))
            out.append('    </workflowContextParameterMap>')
        out.append('    <sequence>%d</sequence>'%tc['sequence'])
        out.append('  </TestCase>')
    out.append('</TestCases>')
    return '\n'.join(out)+'\n'

def main():
    tree=ET.parse(sys.argv[1]); root=tree.getroot()
    cases,unsupported=build_cases(root)
    print("cases=%d unsupported=%d"%(len(cases),len(unsupported)))
    for tc in cases:
        print(json.dumps(tc, ensure_ascii=False))
    for u in unsupported: print("  ADVISORY: %s :: %s"%(u[1],u[2]))
    xml=to_xml(cases)
    import os
    outdir=sys.argv[3] if len(sys.argv)>3 else '.'; os.makedirs(outdir,exist_ok=True)
    name=sys.argv[2] if len(sys.argv)>2 else 'imported'
    p=os.path.join(outdir,name+'.xml'); open(p,'w',encoding='utf-8').write(xml)
    # well-formedness check
    ET.fromstring(xml); print("well-formed XML -> %s"%p)

if __name__=='__main__': main()
