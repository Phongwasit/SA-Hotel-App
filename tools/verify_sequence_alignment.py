"""Verify compiled class ownership and invocation order against the approved sequence contract.
Run after Test-Hotel.ps1 / Maven package: py -3.12 tools/verify_sequence_alignment.py
No server connection or database writes.
"""
from pathlib import Path
import subprocess,os,re,json,shutil
app=Path(__file__).resolve().parents[1]
spec=json.loads((app/'docs/sequence-method-contract.json').read_text(encoding='utf-8'))
javap=Path(os.environ['JAVA_HOME'])/'bin/javap.exe' if os.environ.get('JAVA_HOME') else Path(shutil.which('javap'))
methods={};errors=[]
for cls,required in spec['required'].items():
    package='model' if cls=='RoomCollection' else 'ui' if cls.endswith('UI') else 'controller'
    result=subprocess.run([str(javap),'-c','-p','-classpath',str(app/'target/classes'),'th.ac.hotel.'+package+'.'+cls],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if result.returncode:raise RuntimeError(result.stderr)
    current=None
    for line in result.stdout.splitlines():
        match=re.match(r'\s+(?:public|private|protected)\s+.*?\s([\w$]+)\([^;]*\).*;',line)
        if match:
            current=cls+'.'+match[1];methods.setdefault(current,[])
        invoked=re.search(r'// (?:InterfaceMethod|Method) ([^:]+):',line)
        if current and invoked:methods[current].append(invoked[1].split('.')[-1])
    for method in required:
        if cls+'.'+method not in methods:errors.append('Missing operation '+cls+'.'+method)
for operation,expected in spec['flows'].items():
    actual=methods.get(operation,[]);cursor=0
    for call in expected:
        try:cursor=actual.index(call,cursor)+1
        except ValueError:errors.append(operation+' missing/out-of-order '+call+'; actual '+str(actual));break
for operation,file in spec['browser'].items():
    code=(app/'src/main/resources/static/js'/file).read_text(encoding='utf-8')
    if not re.search(r'function\s+'+operation+r'\s*\(',code):errors.append('Missing browser operation '+operation)
    if len(re.findall(r'\b'+operation+r'\b',code))<2:errors.append('Uncalled browser operation '+operation)
if errors:raise SystemExit('FAIL\n'+'\n'.join(errors))
print('PASS: '+str(sum(map(len,spec['required'].values())))+' operations in '+str(len(spec['required']))+' responsible classes; '+str(len(spec['flows']))+' compiled call-order paths; browser submit/print wired.')
