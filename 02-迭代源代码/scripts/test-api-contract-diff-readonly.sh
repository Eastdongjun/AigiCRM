#!/usr/bin/env bash
# check-api-contract-diff.sh 的行为测试(2026-11-24)
#
# 这个契约脚本的语义是"已提交的机器契约 vs 源码现状", 并保证手改工作区的 json 绕不过检测。
# 此前它把生成结果直接写回 docs/api/api-list.md(+同名 .json): 一个"检查"脚本每次运行都改被跟踪的文件
# (生成时间行必变), 跑完契约集工作区就是脏的 —— 而部署/上传守卫要求工作区干净。
#
# 验证(在临时 git 仓库里做, 不碰真实仓库):
#   ① 跑完检查后, 被跟踪的两个文件逐字节不变(夹具里的生成日期故意写得很旧, 改写就一定看得出来)
#   ② 检测器仍然管用: 手改 json 删掉一个端点, NOGEN 模式下报"破坏性变更"
#   ③ 正常模式下比较用的是源码生成结果, 不是工作区里手改的 json
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_SELF_DIR/.." && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }
for f in check-api-contract-diff.sh gen-api.py exit-guard.sh; do
  [ -f "$TEST_SELF_DIR/$f" ] || fail "缺少 scripts/$f"
done
CTRL=module/system/controller/SysMenuController.java     # 体量最小的真实控制器之一(两个接口), 作夹具源码
[ -f "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/$CTRL" ] || fail "缺少夹具源码 $CTRL"

T="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-apidiff-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/scripts" "$T/docs/api" "$T/aigicrm-backend/src/main/java/com/aigicrm/$(dirname "$CTRL")"
# 被测脚本从它自己所在的目录加载退出码守卫, 副本环境里也要有这一份
cp "$TEST_SELF_DIR/check-api-contract-diff.sh" "$TEST_SELF_DIR/gen-api.py" "$TEST_SELF_DIR/exit-guard.sh" "$T/scripts/"
# 夹具自洽: 生成器只肯读写"自己所在项目目录"或系统临时目录, 所以源码也放一份副本, 不用软链指向真实仓库
cp "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/$CTRL" "$T/aigicrm-backend/src/main/java/com/aigicrm/$CTRL"

# 夹具里的清单: 用夹具自己的生成器对着这一份控制器生成, 再把日期改得很早。
# 之后每跑一次检查, 如果脚本改写文件, 日期就会变成今天。
python3 "$T/scripts/gen-api.py" "$T/aigicrm-backend/src/main/java/com/aigicrm" "$T/docs/api/api-list.md" > "$T/gen.out" 2>&1 \
  || fail "夹具准备失败: 生成器没能对着夹具源码生成清单: $(cat "$T/gen.out")"
[ -s "$T/docs/api/api-list.json" ] || fail "夹具准备失败: 没有生成 json"
sed -i '' -E 's/^> 生成时间: [0-9-]+/> 生成时间: 2000-01-01/' "$T/docs/api/api-list.md"
sed -i '' -E 's/"generated_at": "[0-9-]+"/"generated_at": "2000-01-01"/' "$T/docs/api/api-list.json"
grep -q '生成时间: 2000-01-01' "$T/docs/api/api-list.md" || fail "夹具准备失败: md 的生成时间没替换成功"
grep -q '"generated_at": "2000-01-01"' "$T/docs/api/api-list.json" || fail "夹具准备失败: json 的 generated_at 没替换成功"
# 脚本从版本库取"已提交"的 json: 没有提交时退回到暂存区, 所以只需 init + add
git -C "$T" init -q
git -C "$T" add -A >/dev/null 2>&1

sha() { shasum -a 256 "$T/docs/api/api-list.md" "$T/docs/api/api-list.json" | awk '{ print $1 }' | tr '\n' ' '; }
run_check() { bash "$T/scripts/check-api-contract-diff.sh" > "$T/out" 2>&1; }    # 返回检查脚本的退出码

# ---- ① 跑完检查后文件不变 ----
before="$(sha)"
rc=0; run_check || rc=$?
[ "$rc" -eq 0 ] || fail "无接口变更时检查应通过(rc=${rc}): $(cat "$T/out")"
after="$(sha)"
[ "$before" = "$after" ] || fail "检查脚本改写了被跟踪的文件(部署/上传守卫要求工作区干净): $(git -C "$T" status --porcelain | tr '\n' ' ')"
grep -q '生成时间: 2000-01-01' "$T/docs/api/api-list.md" || fail "docs/api/api-list.md 被改写了"

# ---- ② 检测器仍然管用: 手改 json 删掉一个端点, NOGEN 模式下报破坏性变更 ----
cp "$T/docs/api/api-list.json" "$T/pristine.json"
python3 - "$T/docs/api/api-list.json" <<'PY'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding='utf-8'))
d['endpoints'].pop()
json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY
rc=0; AIGI_API_CONTRACT_NOGEN=1 run_check || rc=$?
[ "$rc" -eq 1 ] || fail "手改 json 删掉端点后, NOGEN 模式应报破坏性变更(rc=${rc}): $(cat "$T/out")"
grep -q '破坏性变更' "$T/out" || fail "应说明检测到破坏性变更: $(cat "$T/out")"

# ---- ③ 正常模式下比较的是源码生成结果: 工作区里手改的 json 不参与比较 ----
rc=0; run_check || rc=$?
[ "$rc" -eq 0 ] || fail "正常模式应以源码生成结果为准, 不应受工作区手改的 json 影响(rc=${rc}): $(cat "$T/out")"
grep -q 'PASS' "$T/out" || fail "正常模式应输出 PASS: $(cat "$T/out")"

echo "PASS: check-api-contract-diff.sh 不改被跟踪的文件 / 检测器仍能发现删端点 / 正常模式以源码生成结果为准。"
