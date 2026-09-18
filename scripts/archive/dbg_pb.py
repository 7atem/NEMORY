import sys
data = open(sys.argv[1], 'rb').read()
def rv(b, i):
    r = 0; s = 0
    while True:
        x = b[i]; i += 1
        r |= (x & 0x7f) << s
        if not (x & 0x80): return r, i
        s += 7
def parse(b, depth=0):
    out = []; i = 0; n = len(b)
    while i < n:
        tag, i = rv(b, i); f, w = tag >> 3, tag & 7
        if w == 0:
            v, i = rv(b, i)
        elif w == 1:
            v = b[i:i+8]; i += 8
        elif w == 5:
            v = b[i:i+4]; i += 4
        elif w == 2:
            ln, i = rv(b, i); v = b[i:i+ln]; i += ln
        else:
            raise ValueError('wt')
        out.append((f, w, v))
    return out
def dump(fields, depth=0):
    for f, w, v in fields:
        if w == 2:
            try:
                s = v.decode('utf-8')
                printable = all(ord(c) >= 0x20 or c in '\n\t' for c in s)
            except Exception:
                printable = False
            if printable and len(s) < 100 and s.strip():
                print('  ' * depth + 'f%d str %r' % (f, s))
            else:
                print('  ' * depth + 'f%d msg[%d]' % (f, len(v)))
                try:
                    dump(parse(v), depth + 1)
                except Exception:
                    print('  ' * depth + '  (unparseable)')
        else:
            print('  ' * depth + 'f%d varint %r' % (f, v))
target = sys.argv[2].encode()
def find(fields, depth=0):
    for f, w, v in fields:
        if w == 2:
            if v == target:
                return fields
            try:
                r = find(parse(v), depth + 1)
                if r: return r
            except Exception:
                pass
    return None
hit = find(parse(data))
if hit:
    dump(hit)
else:
    print('not found')
