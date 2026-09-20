#!/usr/bin/env bash
# 【PR-22.COVERAGE 2026-09-20】覆盖登记表契约: 覆盖记录与关闭状态分离的台账门禁
#
# 校验 00-项目管理/PR22-覆盖登记表.md:
# 1. §1 覆盖表与 §2 问题表的每一行状态必须取自七态集合
#    (已列入/已复现/已修改/已回归/已关闭/不适用/待验证);
# 2. §3 统计行与两表实际行数逐态一致(自一致锚点, 防账实分离);
# 3. 已关闭/已回归行必须引用证据(runs/<id> 或 commit 短号 4-40 位十六进制)。
# G-01 教训的结构化落地: 覆盖与关闭是两张表, 互不冒充。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"  # 工作区根(账本在 00-项目管理, 脚本在 02-迭代源代码/scripts)
LEDGER="$ROOT/00-项目管理/PR22-覆盖登记表.md"

fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$LEDGER" ] || fail "coverage ledger missing: $LEDGER"

python3 - "$LEDGER" <<'PYEOF'
import re
import sys

path = sys.argv[1]
text = open(path, encoding='utf-8').read()
STATES = ['已列入', '已复现', '已修改', '已回归', '已关闭', '不适用', '待验证']

def parse_rows(section_marker, next_marker):
    seg = text.split(section_marker, 1)[1]
    seg = seg.split(next_marker, 1)[0] if next_marker in seg else seg
    rows = []
    for line in seg.splitlines():
        line = line.strip()
        if not line.startswith('|') or re.match(r'^\|[\s\-|]+$', line):
            continue
        cells = [c.strip() for c in line.strip('|').split('|')]
        if len(cells) < 2 or cells[0] in ('ID', '---', ':---'):
            continue
        if not re.match(r'^(CV|DF)-\d+$', cells[0]):
            continue
        rows.append(cells)
    return rows

cv = parse_rows('## §1', '## §2')
df = parse_rows('## §2', '## §3')
if not cv:
    sys.exit('FAIL: §1 覆盖表无有效行')
if not df:
    sys.exit('FAIL: §2 问题表无有效行')

errors = []
# 状态列: §1 倒数第二列(状态), 最后一列为证据; §2 第4列状态/第5列修复/第6列回归证据
for cells in cv:
    state = cells[-2]
    if state not in STATES:
        errors.append(f"§1 {cells[0]} 非法状态: {state}")
    if state in ('已关闭', '已回归') and not re.search(r'runs/[\w.\-]+|[0-9a-f]{7,40}', cells[-1]):
        errors.append(f"§1 {cells[0]} 状态 {state} 缺证据引用")
for cells in df:
    state = cells[3]
    if state not in STATES:
        errors.append(f"§2 {cells[0]} 非法状态: {state}")
    blob = ' '.join(cells[4:])
    if state in ('已关闭', '已回归') and not re.search(r'runs/[\w.\-]+|[0-9a-f]{7,40}|用例|矩阵|测试', blob):
        errors.append(f"§2 {cells[0]} 状态 {state} 缺证据引用")

if errors:
    sys.exit('FAIL: coverage ledger violations:\n  ' + '\n  '.join(errors))

def counts(rows, state_idx):
    out = {s: 0 for s in STATES}
    for cells in rows:
        s = cells[state_idx]
        out[s] = out.get(s, 0) + 1
    return out

cv_counts = counts(cv, -2)
df_counts = counts(df, 3)
stats = text.split('## §3', 1)[1] if '## §3' in text else ''
m1 = re.search(r'覆盖行[:：]\s*(\d+)\s*=\s*(.+)', stats)
m2 = re.search(r'问题行[:：]\s*(\d+)\s*=\s*(.+)', stats)
if not m1 or not m2:
    sys.exit('FAIL: §3 统计行缺失或格式不符')
if int(m1.group(1)) != len(cv):
    errors.append(f"§3 覆盖行总数 {m1.group(1)} ≠ 实际 {len(cv)}")
if int(m2.group(1)) != len(df):
    errors.append(f"§3 问题行总数 {m2.group(1)} ≠ 实际 {len(df)}")
for label, actual, declared in (('覆盖', cv_counts, m1.group(2)), ('问题', df_counts, m2.group(2))):
    for state, n in actual.items():
        pat = re.escape(state) + r'\s+' + str(n)
        if n > 0 and not re.search(pat, declared):
            errors.append(f"§3 {label}行 {state} 实际 {n} 与声明不符: {declared}")
        if n == 0 and re.search(re.escape(state) + r'\s+[1-9]', declared):
            errors.append(f"§3 {label}行声明 {state}>0 但实际为 0")
if errors:
    sys.exit('FAIL: coverage ledger violations:\n  ' + '\n  '.join(errors))
print(f"PASS: coverage ledger aligned ({len(cv)} coverage rows, {len(df)} defect rows, states valid, stats consistent).")
PYEOF
