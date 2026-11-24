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

# 从源码重生成契约到临时文件(md+json), 再与已提交的 json 比较 —— 真实语义: committed 契约 vs 源码现状。
# 【修正 2026-11-24】此前直接写回 docs/api/api-list.md(+同名 .json): 一个"检查"脚本每次运行都改被跟踪的文件
#   (生成时间行必变), 跑完契约集工作区就是脏的, 而部署/小程序上传守卫要求工作区干净。
#   改为生成到临时文件后语义不变: 比较用的"现状"来自生成结果, 不读工作区里可能被手改的 json。
# AIGI_API_CONTRACT_NOGEN=1 供负例自证(跳过 regen, 直接拿工作区里手改的 json 验证检测器会红; 正常门禁勿设)。
NEW_JSON="$COMMITTED"
# 【退出码守卫】见 exit-guard.sh: bash 3.2 在 set -e + EXIT 陷阱下会把未定义变量引起的中止报成退出码 0。
#   检查脚本因此会在"其实没检查完"的情况下显示通过。
cleanup_gen_dir() { if [ -n "${GEN_DIR:-}" ]; then rm -rf "$GEN_DIR"; fi; }
. "$(cd "$(dirname "$0")" && pwd)/exit-guard.sh"
exit_guard_install cleanup_gen_dir
if [ "${AIGI_API_CONTRACT_NOGEN:-0}" != "1" ]; then
  GEN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/api-contract-diff.XXXXXX")"
  # 生成失败要出声: 旧写法把生成器的报错丢进 /dev/null, 失败时只剩一个没有说明的退出码 1
  if ! python3 "$SCRIPT_DIR/gen-api.py" "$ROOT/aigicrm-backend/src/main/java/com/aigicrm" "$GEN_DIR/api-list.md" > "$GEN_DIR/gen.out" 2>&1; then
    echo "FAIL: gen-api.py 生成失败:" >&2; tail -5 "$GEN_DIR/gen.out" >&2; exit 1
  fi
  NEW_JSON="$GEN_DIR/api-list.json"
fi

python3 - "$ROOT" "$NEW_JSON" <<'PY'
import json, subprocess, sys, os
root = sys.argv[1]
# 仓库根可能在 $ROOT 之上(仓库根=01-AIGICRM, $ROOT=02-迭代源代码)——git show 的路径
# 必须相对仓库根, 用 rev-parse 定位后 relpath 换算(2026-11-11 负例自证抓出的路径坑)
repo = subprocess.run(['git', '-C', root, 'rev-parse', '--show-toplevel'],
                      capture_output=True, text=True).stdout.strip()
if not repo:
    print('FAIL: 不在 git 仓库中'); sys.exit(1)
# 【CODE-REVIEW-20261123 R2-02】工作目录可能是符号链接(实测 01-AIGICRM -> Agent/.../aigicrm/source),
# 而 rev-parse 返回真实路径 → 未 realpath 时两者无公共前缀, relpath 得到 "../../../../.." 垃圾,
# git show 必然失败并误报"不在版本控制中", 导致从符号链接入口跑时权威门禁恒红。两侧统一取 realpath。
committed_abs = os.path.realpath(os.path.join(root, 'docs/api/api-list.json'))
rel = os.path.relpath(committed_abs, os.path.realpath(repo))
def committed_text():
    for ref in ('HEAD:' + rel, ':' + rel):
        r = subprocess.run(['git', '-C', repo, 'show', ref], capture_output=True, text=True)
        if r.returncode == 0:
            return r.stdout
    print('FAIL: %s 不在版本控制中(先提交)' % rel); sys.exit(1)

old = json.loads(committed_text())
# "现状": 正常模式下来自临时生成的 json; NOGEN 模式下来自工作区的 json(负例自证用)
new = json.loads(open(sys.argv[2], encoding='utf-8').read())
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
    print('  若为有意变更: 运行 python3 scripts/gen-api.py aigicrm-backend/src/main/java/com/aigicrm docs/api/api-list.md 后, 提交更新后的 docs/api/api-list.json(评审将看到该 diff)。', file=sys.stderr)
    sys.exit(1)
print(f"PASS: API 机器契约无破坏性变更({len(ni)} 端点; 新增 {len(added)})")
PY
EXIT_GUARD_DONE=1
