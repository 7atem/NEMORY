"""Local physical-device UX runner. Does not clear application data."""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
OUT = Path(__file__).resolve().parent

def adb(*args):
    return subprocess.check_output([ADB, '-s', 'R52R40LBEQZ', *args])

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/nemory-ux.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/nemory-ux.xml'))

def tap(label):
    root = tree()
    for node in root.iter('node'):
        if label in (node.get('text'), node.get('content-desc')):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            if x2 > x1 and y2 > y1:
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                print('Tapped:', label)
                return
    raise RuntimeError('Visible control not found: ' + label)

def snapshot(name):
    root = tree()
    (OUT / (name + '.xml')).write_bytes(ET.tostring(root))
    raw = adb('exec-out', 'screencap', '-p')
    if raw:
        (OUT / (name + '.png')).write_bytes(raw)
    print('Snapshot:', name, 'PNG bytes:', len(raw))
    for node in root.iter('node'):
        label = node.get('text') or node.get('content-desc')
        if label and node.get('password') != 'true':
            print(label[:110], node.get('bounds'))

if __name__ == '__main__':
    op, *args = sys.argv[1:]
    if op == 'shot': snapshot(args[0])
    elif op == 'tap': tap(args[0])
    elif op == 'key': adb('shell', 'input', 'keyevent', args[0])
    elif op == 'swipe': adb('shell', 'input', 'swipe', *args)
    elif op == 'type': adb('shell', 'input', 'text', args[0].replace(' ', '%s'))
