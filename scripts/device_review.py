#!/usr/bin/env python3
"""Small host driver: select actual UI nodes, retain XML next to native screenshots."""
import argparse, json, os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
ADB = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
PACKAGE = 'staff.android.commerce'
ROOT = Path(__file__).resolve().parents[1]
def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args])
def shell(*args):
    return adb('shell', *args).decode().strip()
def tree():
    for attempt in range(4):
        shell('rm', '-f', '/sdcard/common-goods-review.xml')
        result = subprocess.run([ADB, '-s', SERIAL, 'shell', 'uiautomator', 'dump',
            '/sdcard/common-goods-review.xml'], capture_output=True, text=True, check=False)
        if result.returncode == 0 and 'UI hier' in result.stdout:
            raw = adb('exec-out', 'cat', '/sdcard/common-goods-review.xml')
            return raw, ET.fromstring(raw)
        time.sleep(.6)
    raise RuntimeError('Could not capture a fresh UI tree: ' + result.stdout + result.stderr)
def nodes():
    _, root = tree()
    return [{k:n.get(k) for k in ('text','content-desc','resource-id','bounds','clickable','enabled','selected')} for n in root.iter('node') if n.get('text') or n.get('content-desc') or n.get('class') == 'android.widget.EditText']
def tap(value, attr='text'):
    _, root = tree()
    hits=[n for n in root.iter('node') if n.get(attr)==value]
    if len(hits)!=1: raise RuntimeError(f'{attr}={value!r}: expected 1 node, got {len(hits)}')
    n=hits[0]
    if n.get('enabled')!='true': raise RuntimeError(f'Disabled: {value}')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    shell('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)
def capture(name):
    folder=ROOT/'evidence/ui/review';folder.mkdir(parents=True,exist_ok=True)
    raw,_=tree();(folder/f'{name}.xml').write_bytes(raw)
    (folder/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
    print(folder/f'{name}.png')
def final_capture(name):
    folder=ROOT/'evidence/ui/final';folder.mkdir(parents=True,exist_ok=True)
    time.sleep(.6)
    raw,_=tree();(folder/f'{name}.xml').write_bytes(raw)
    (folder/f'{name}.png').write_bytes(adb('exec-out','screencap','-p'))
    print(name, flush=True)
def scroll_to_text(value, attempts=8):
    for _ in range(attempts):
        _,root=tree()
        if any(n.get('text')==value for n in root.iter('node')): return
        scrolls=[n for n in root.iter('node') if n.get('scrollable')=='true']
        if not scrolls: raise RuntimeError('No scrolling container for '+value)
        def rect(n): return list(map(int,re.findall(r'\d+', n.get('bounds'))))
        def area(n):
            a,b,c,d=rect(n);return (c-a)*(d-b)
        x1,y1,x2,y2=rect(max(scrolls,key=area))
        shell('input','swipe',str((x1+x2)//2),str(y1+(y2-y1)*8//10),str((x1+x2)//2),str(y1+(y2-y1)*2//10),'450')
        time.sleep(.4)
    raise RuntimeError('Not reached: '+value)
def start():
    shell('am','start','-W','-n',PACKAGE+'/.MainActivity');time.sleep(1.2)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['nodes','tap','desc','capture','start']);p.add_argument('value',nargs='?');a=p.parse_args()
    if a.action=='nodes': print(json.dumps(nodes(),ensure_ascii=False,indent=2))
    elif a.action in ('tap','desc'): tap(a.value,'content-desc' if a.action=='desc' else 'text')
    elif a.action=='capture': capture(a.value)
    elif a.action=='start': start()
