#!/bin/bash
# 【底座收敛 P1 2026-11-11 → T9 2026-11-12 独立真值改造】权限码生成物新鲜度契约。
# 旧形态缺陷(D8"自指恒 PASS"): 跑 gen-permission-codes.py 再 git diff——生成器漏码时
# 生成物同样缺码, diff 恒空恒绿, 掩盖漏码(实证: 多参 hasAnyAuthority 漏 5 码一个月无人知)。
# 新形态(独立真值): 本契约自带一段与生成器**独立实现**的抽取逻辑,
# 从后端源码抽权威码 ∪ FE_ONLY 白名单, 与 committed 生成物逐码比对——生成器坏了必须红。
# 另保留"生成物未手改"维度: 重跑生成器后 diff 须空(两检查互为正交防线)。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }

cd "$ROOT"
GEN_UI="aigicrm-ui/src/generated/permission-codes.js"
GEN_MP="aigicrm-miniprogram/generated/permission-codes.js"
for f in "$GEN_UI" "$GEN_MP"; do
  [ -f "$ROOT/$f" ] || fail "生成物缺失: $f"
  git -C "$ROOT" ls-files --error-unmatch "$f" >/dev/null 2>&1 || fail "生成物未纳入版本控制: $f"
done

# ---- ① 独立真值抽取(与 gen-permission-codes.py 实现不同源: 逐行引号对扫描而非参数段正则) ----
python3 - "$ROOT" <<'PY' > /tmp/t9-truth-codes.txt
import re, sys
from pathlib import Path
root = Path(sys.argv[1])
codes = set()
for f in (root / "aigicrm-backend/src/main/java").rglob("*.java"):
    for line in f.read_text(errors="replace").splitlines():
        s = line.strip()
        if s.startswith(("*", "//", "/*")):
            continue
        if "Authority(" in line:
            codes.update(re.findall(r"'([a-z][a-z0-9:_-]+)'", line))
        if 'SecurityUtils.hasAuthority("' in line:
            m = re.search(r'SecurityUtils\.hasAuthority\("([a-z][a-z0-9:_-]+)"\)', line)
            if m:
                codes.add(m.group(1))
gen = Path(root / "scripts/gen-permission-codes.py").read_text()
fe = re.search(r"FE_ONLY_CODES = \[(.*?)\]", gen, re.S)
if fe:
    codes.update(re.findall(r'"([a-z][a-z0-9:_-]+)"', fe.group(1)))
for c in sorted(codes):
    print(c)
PY

gen_codes=$(grep -oE ": '[a-z][a-z0-9:_-]+'" "$GEN_UI" | sed "s/^: '//; s/'$//" | sort -u)
truth_codes=$(sort -u /tmp/t9-truth-codes.txt)

if [ "$gen_codes" != "$truth_codes" ]; then
  echo "  [独立真值≠生成物] 差异(左=真值 右=生成物):" >&2
  diff <(echo "$truth_codes") <(echo "$gen_codes") | sed 's/^/  /' >&2 || true
  fail "生成物与独立真值漂移——生成器可能漏码/多码, 修复 gen-permission-codes.py 后重生成"
fi

# ---- ② 双端一致 + 未手改(重跑生成器 diff 须空) ----
mp_codes=$(grep -oE ": '[a-z][a-z0-9:_-]+'" "$GEN_MP" | sed "s/^: '//; s/'$//" | sort -u)
[ "$gen_codes" = "$mp_codes" ] || fail "PC/MP 双端生成物码集不一致"
python3 "$SCRIPT_DIR/gen-permission-codes.py" >/dev/null || fail "生成器执行失败"
git diff --quiet -- "$GEN_UI" "$GEN_MP" \
  || { git diff -- "$GEN_UI" | head -10 | sed 's/^/  /' >&2
       fail "重跑生成器产生 diff——生成物被手改或环境异常, 提交再生成的产物"; }

cnt=$(echo "$gen_codes" | grep -c .)
echo "PASS: 权限码生成物=独立真值($cnt 码, PC/MP 一致, 独立抽取器与生成器双源互证)"
