import re
lines = open(r'C:/Users/tech_/AppData/Local/Temp/ekl_old.txt', encoding='utf-8').read().split('\n')
start = next(i for i, l in enumerate(lines) if l.strip() == 'static {};')
stack, vars_ = [], {}
entries = None
instr_re = re.compile(r'^\s+\d+: (\S+)(.*)$')
ldc_re = re.compile(r'// String (.*)$')
for l in lines[start + 1:]:
    m = instr_re.match(l)
    if not m:
        continue
    op, rest = m.group(1), m.group(2)
    if op in ('bipush', 'sipush'):
        stack.append(int(rest.strip()))
    elif op.startswith('iconst_'):
        stack.append(int(op[7:]))
    elif op in ('ldc', 'ldc_w'):
        sm = ldc_re.search(rest)
        stack.append(sm.group(1) if sm else 'LDC_OTHER')
    elif op == 'anewarray':
        n = stack.pop()
        stack.append(['arr', [None] * n])
    elif op.startswith('astore'):
        idx = op[7:] if '_' in op else rest.strip()
        vars_[int(idx)] = stack.pop()
    elif op.startswith('aload'):
        idx = op[6:] if '_' in op else rest.strip()
        stack.append(vars_[int(idx)])
    elif op == 'aastore':
        v = stack.pop(); i = stack.pop(); a = stack.pop()
        a[1][i] = v
    elif op == 'dup':
        stack.append(stack[-1])
    elif op == 'new':
        stack.append('obj')
    elif op == 'invokespecial':
        stack.pop()
    elif op == 'putstatic':
        if stack:
            stack.pop()
    elif op == 'invokedynamic':
        stack.append('fn')
    elif op == 'invokestatic':
        if 'SetsKt.setOf' in rest:
            a = stack.pop(); stack.append(('set', a[1]))
        elif 'CollectionsKt.listOf' in rest:
            a = stack.pop(); stack.append(('list', a[1]))
        elif 'TuplesKt.to' in rest:
            b = stack.pop(); a = stack.pop(); stack.append(('pair', a, b))
        elif 'MapsKt.mapOf' in rest:
            a = stack.pop(); entries = a[1]
        elif 'LazyKt.lazy' in rest:
            stack.pop(); stack.append('lazy')
assert entries, 'mapOf not found'

def esc(s):
    return s.replace(chr(92), chr(92) * 2).replace('"', chr(92) + '"')

out = []
for e in entries:
    assert e[0] == 'pair', e
    expid, groups = e[1], e[2]
    assert groups[0] == 'list'
    out.append('        ExperienceId.%s to listOf(' % expid)
    for gi, g in enumerate(groups[1]):
        assert g[0] == 'set'
        kws = ', '.join('"%s"' % esc(k) for k in g[1])
        comma = ',' if gi < len(groups[1]) - 1 else ''
        out.append('            setOf(%s)%s' % (kws, comma))
    out.append('        ),')
out[-1] = out[-1].rstrip(',')
open(r'C:/Users/tech_/AppData/Local/Temp/keywords_recovered.kt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('entries:', len(entries), 'groups:', sum(len(e[2][1]) for e in entries))
