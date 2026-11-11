#!/bin/bash
# 【底座收敛 2026-11-11】API 机器契约破坏性变更检测:
# docs/api/api-list.json(端点×方法×权限码) 与源码生成的差异分三级——
#   破坏性(红): 删除端点 / 改方法 / 改权限码(收紧或放松都是行为变更, 须显式知情)
#   新增(绿): 打印 INFO
#   无差异: PASS
# 与 check-api-list-fresh.sh(md 内容级)互补: 本契约只看机器可读三元组, md 是人读面。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }

COMMITTED="$ROOT/docs/api/api-list.json"
[ -f "$COMMITTED" ] || fail "机器契约不存在: docs/api/api-list.json(先跑 gen-api.py)"

# 从源码重生成契约(md+json 一并刷新)——真实语义: committed 契约 vs 源码现状。
# AIGI_API_CONTRACT_NOGEN=1 供负例自证(跳过 regen, 直接拿手改 json 验证检测器会红;
# 正常门禁勿设——regen 会复原任何对 json 的手改, 这正是"机器契约不可手绕"的机制)。
if [ "${AIGI_API_CONTRACT_NOGEN:-0}" != "1" ]; then
  python3 "$SCRIPT_DIR/gen-api.py" "$ROOT/aigicrm-backend/src/main/java/com/aigicrm" "$ROOT/docs/api/api-list.md" >/dev/null 2>&1
fi

python3 - "$ROOT" <<'PY'
import json, subprocess, sys, os
root = sys.argv[1]
# 仓库根可能在 $ROOT 之上(仓库根=01-AIGICRM, $ROOT=02-迭代源代码)——git show 的路径
# 必须相对仓库根, 用 rev-parse 定位后 relpath 换算(2026-11-11 负例自证抓出的路径坑)
repo = subprocess.run(['git', '-C', root, 'rev-parse', '--show-toplevel'],
                      capture_output=True, text=True).stdout.strip()
if not repo:
    print('FAIL: 不在 git 仓库中'); sys.exit(1)
rel = os.path.relpath(os.path.join(root, 'docs/api/api-list.json'), repo)
def committed_text():
    for ref in ('HEAD:' + rel, ':' + rel):
        r = subprocess.run(['git', '-C', repo, 'show', ref], capture_output=True, text=True)
        if r.returncode == 0:
            return r.stdout
    print('FAIL: %s 不在版本控制中(先提交)' % rel); sys.exit(1)

old = json.loads(committed_text())
new = json.loads(open(os.path.join(root, 'docs/api/api-list.json'), encoding='utf-8').read())
def index(doc):
    return {(e['method'], e['path']): e['permission'] for e in doc['endpoints']}
oi, ni = index(old), index(new)

removed = sorted(set(oi) - set(ni))
added = sorted(set(ni) - set(oi))
changed = sorted(k for k in set(oi) & set(ni) if oi[k] != ni[k])

breaking = []
for m, p in removed:
    breaking.append(f'删除端点 {m} {p} (原权限: {oi[(m,p)]})')
for m, p in changed:
    breaking.append(f'权限码变更 {m} {p}: {oi[(m,p)]} -> {ni[(m,p)]}')
for m, p in added:
    print(f'  [新增] {m} {p} ({ni[(m,p)]})')

if breaking:
    print('FAIL: API 机器契约检测到破坏性变更(删端点/改权限码):', file=sys.stderr)
    for b in breaking:
        print(f'  [破坏性] {b}', file=sys.stderr)
    print('  若为有意变更: 提交更新后的 docs/api/api-list.json 使其入版本库(评审将看到该 diff)。', file=sys.stderr)
    sys.exit(1)
print(f"PASS: API 机器契约无破坏性变更({len(ni)} 端点; 新增 {len(added)})")
PY
