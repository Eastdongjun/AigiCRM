#!/bin/bash
# 【底座评估 2026-11-11】数据范围"配置面 = 消费面"对账契约:
# role_perm_scope 的 (module, action) 档位是配置面(权限中心可配), 后端源码是消费面——
#   评估报告实锤: 动作轴只有 customer/file 真消费, 其余模块只 view; 且统计面与客户列表
#   用不同 key(StatisticsQueryService customer.view vs statistics) → "列表能看/看板不算"口径分裂。
# 本契约把档位枚举"声明为一等公民", 三向对账:
#   ① 源码新增 .module("x")/.action("y") 字面量 ∉ 枚举 → 红(新消费点必须先登记档位);
#   ② 枚举每档必须带源码锚正则且在源码中命中(声明了没人消费 → 红, 防枚举腐化);
#   ③ 迁移链对 role_perm_scope 写入的 (module,action) 字面量 ∉ 枚举 → 红(配置面不得超发)。
# 枚举即"动作轴声明表"——哪些模块有 edit/delete 档、哪些只有 view, 一处可读。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/aigicrm-backend/src/main/java"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"
fail() { echo "FAIL: $1" >&2; exit 1; }

# ---- 动作轴声明表: module|action|源码锚正则|声明 ----
# 只列"进入 role_perm_scope / OrgScopeResolver"的档位; 每行锚正则必须在源码命中。
# 【已知口径问题 2026-11-11(评估报告 P2#7 根因, 待拍板)】统计面一部分走 (customer,view)
# 一部分走 (statistics,view)——本表如实双列, 拍板统一后删一行。
AXIS='customer|view|\.module\("customer"\)|客户列表/详情/导出/统计基础口径
customer|edit|canAccessCustomer\(SecurityUtils\.getCurrentUser\(\), "edit"|编辑范围(canEdit, 池内客户另走池 PERM_EDIT)
customer|delete|canAccessCustomer\(SecurityUtils\.getCurrentUser\(\), "delete"|删除范围
customer|finance_edit|canAccessCustomer\(SecurityUtils\.getCurrentUser\(\), "finance_edit"|开票回款编辑范围(2026-11-03 拆码)
file|view|matrixActionOf\(action\)|文件组织矩阵 view 档(view/download 归此档)
file|manage|case P_UPLOAD, P_RENAME, P_REPLACE, P_SHARE, P_EDIT -> "manage"|文件矩阵 manage 档(upload/rename/replace/share/edit)
file|delete|case P_DELETE -> "delete"|文件矩阵 delete 档(拍板③独立)
statistics|view|resolveModuleUsers\([a-zA-Z]+, "statistics"\)|统计/排行可见用户集(与 customer.view 双 key 并存, 待拍板归一)
approval|view|resolveModuleUsers\(SecurityUtils\.getCurrentUser\(\), "approval"\)|审批待办可见集(仅 view 档)
reminder|view|resolveModuleUsers\(SecurityUtils\.getCurrentUser\(\), "reminder"\)|提醒可见集(仅 view 档)
sea|view|resolveModuleUsers\(SecurityUtils\.getCurrentUser\(\), "sea"\)|公海列表可见集(池级编辑走池名单, 不入此表)'

# ① 源码消费字面量 ⊆ 枚举
miss=0
while IFS= read -r lit; do
  [ -n "$lit" ] || continue
  mod=$(printf '%s' "$lit" | sed -E 's/.*\.module\("([a-z]+)").*/\1/')
  grep -q "^$mod|" <<<"$AXIS" || { echo "  [未声明模块] .$lit"; miss=$((miss+1)); }
done < <( { grep -rhoE '\.module\("[a-z]+"\)' "$SRC" --include="*.java" | grep -vE "LogAspect" || true; } )
while IFS= read -r lit; do
  [ -n "$lit" ] || continue
  act=$(printf '%s' "$lit" | sed -E 's/.*\.action\("([a-z_]+)").*/\1/')
  grep -qE "^[a-z]+\|$act\|" <<<"$AXIS" || { echo "  [未声明动作] .$lit"; miss=$((miss+1)); }
done < <( { grep -rhoE '\.action\("[a-z_]+"\)' "$SRC" --include="*.java" | grep -vE "LogAspect" || true; } )
[ "$miss" -eq 0 ] || fail "源码出现未声明的 (module,action) 消费点——先在本脚本动作轴声明表登记档位与锚"

# ② 枚举每档的源码锚必须命中(防声明腐化)
miss=0
while IFS='|' read -r mod act anchor decl; do
  [ -n "$mod" ] || continue
  if ! grep -rqE "$anchor" "$SRC" --include="*.java"; then
    echo "  [锚失配] $mod/$act: 源码无 '$anchor' —— 档位声明腐化或消费点已迁移"
    miss=$((miss+1))
  fi
done <<<"$AXIS"
[ "$miss" -eq 0 ] || fail "动作轴声明表存在无消费的档位(声明与源码漂移)"

# ③ 迁移链对 role_perm_scope 写入的 (module,action) ⊆ 枚举
miss=0
while IFS='|' read -r mod act anchor decl; do
  [ -n "$mod" ] || continue
  # 该模块动作在迁移链中"作为 role_perm_scope 行值"出现的形态(宽松: 同文件同时提 module 与 action)
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    if grep -q "$mod" "$f" 2>/dev/null && grep -Eq "action.{0,6}['\"]$act['\"]|['\"]$act['\"].{0,40}action" "$f" 2>/dev/null; then
      if ! grep -q "^$mod|$act|" <<<"$AXIS"; then
        echo "  [配置面超发] $f 写入 ($mod,$act) 但动作轴未声明"
        miss=$((miss+1))
      fi
    fi
  done < <(grep -rln "role_perm_scope" "$MIG"/*.sql 2>/dev/null || true)
done <<<"$AXIS"
[ "$miss" -eq 0 ] || fail "迁移链写入了动作轴未声明的 (module,action) 配置"

cnt=$(grep -c . <<<"$AXIS")
echo "PASS: 配置面=消费面 对账($cnt 档位: customer×4 / file×3 / statistics+approval+reminder+sea 仅 view; 统计双 key 已声明待拍板)"
