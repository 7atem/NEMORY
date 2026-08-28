import sys, json

data = open(sys.argv[1], 'rb').read()

def read_varint(b, i):
    r = 0; s = 0
    while True:
        x = b[i]; i += 1
        r |= (x & 0x7f) << s
        if not (x & 0x80): return r, i
        s += 7

def parse(b):
    """Parse protobuf wire format into list of (field, wiretype, value)."""
    out = []; i = 0; n = len(b)
    while i < n:
        tag, i = read_varint(b, i)
        f, w = tag >> 3, tag & 7
        if w == 0:
            v, i = read_varint(b, i)
        elif w == 1:
            v = b[i:i+8]; i += 8
        elif w == 5:
            v = b[i:i+4]; i += 4
        elif w == 2:
            ln, i = read_varint(b, i)
            v = b[i:i+ln]; i += ln
        else:
            raise ValueError('bad wiretype %d at %d' % (w, i))
        out.append((f, w, v))
    return out

def as_str(v):
    try:
        s = v.decode('utf-8')
        if all(c == '\n' or c == '\t' or 0x20 <= ord(c) or ord(c) > 0x7f for c in s):
            return s
    except Exception:
        pass
    return None

results = {}

def walk(fields):
    # Entry message heuristic: has field 2 as string name, and somewhere a nested
    # Item->String value. We detect: collect field-2 string; find string values in subtrees.
    d = dict()
    subs = []
    for f, w, v in fields:
        if w == 2:
            s = as_str(v)
            if s is not None:
                d.setdefault(f, []).append(s)
            else:
                try:
                    subs.append((f, parse(v)))
                except Exception:
                    pass
    names = d.get(2, [])
    if names and names[0].startswith('experience_'):
        # find string value: look into submessages for field-1 strings
        vals = []
        def find_str(fs):
            dd = {}
            for f, w, v in fs:
                if w == 2:
                    s = as_str(v)
                    if s is not None:
                        dd.setdefault(f, []).append(s)
                    else:
                        try:
                            sub_r = find_str(parse(v))
                            for kk, vv in sub_r.items():
                                dd.setdefault(kk, []).extend(vv)
                        except Exception:
                            pass
            return dd
        for f, sub in subs:
            r = find_str(sub)
            for vv in r.values():
                vals.extend(vv)
        # value string: pick the last field-1 string that isn't the name
        cand = [v for v in vals if v != names[0]]
        if cand:
            best = max(cand, key=len)
            if len(best) > len(results.get(names[0], '')):
                results[names[0]] = best
    for f, sub in subs:
        walk(sub)

walk(parse(data))
json.dump(results, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
print(len(results))
