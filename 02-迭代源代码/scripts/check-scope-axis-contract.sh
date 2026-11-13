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
# 【WP1.5 2026-11-13 锁定决策#7】(statistics,view) 数据范围档退役: statistics:view 只做入口码,
# 统计面统一走 (customer,view) 单一源(CustomerVisibilityService)——原"双 key 并存待拍板"行删除,
# 消费点已清零(grep 证), 存量配置行走 V2026.11.13.1 清理。
AXIS='customer|view|\.module\("customer"\)|客户列表/详情/导出/统计基础口径(统计来源动作, WP1.5 单一源)
customer|edit|canAccessCustomer\(SecurityUtils\.getCurrentUser\(\), "edit"|编辑范围(canEdit, 池内客户另走池 PERM_EDIT)
customer|delete|canAccessCustomer\(SecurityUtils\.getCurrentUser\(\), "delete"|删除范围
customer|finance_edit|canAccessCustomer\(SecurityUtils\.getCurrentUser\(\), "finance_edit"|开票回款编辑范围(2026-11-03 拆码)
file|view|matrixActionOf\(action\)|文件组织矩阵 view 档(view/download 归此档)
file|manage|case P_UPLOAD, P_RENAME, P_REPLACE, P_SHARE, P_EDIT -> "manage"|文件矩阵 manage 档(upload/rename/replace/share/edit)
file|delete|case P_DELETE -> "delete"|文件矩阵 delete 档(拍板③独立)
approval|view|resolveModuleUsers\(SecurityUtils\.getCurrentUser\(\), "approval"\)|审批待办可见集(仅 view 档)
reminder|view|resolveModuleUsers\(SecurityUtils\.getCurrentUser\(\), "reminder"\)|提醒可见集(仅 view 档)
sea|view|resolveModuleUsers\(SecurityUtils\.getCurrentUser\(\), "sea"\)|公海列表可见集(池级编辑走池名单, 不入此表)
incentive|stats_view|resolveModuleUsers\(user, "incentive", "stats_view"\)|四项激励独立数据范围(2026-11-05 拍板B, V2026.11.05.7 回填; 其余 7 档为生产死配置见 design.md 已知问题#2)
task|view|resolveModuleUsersWithinGroup\(SecurityUtils\.getCurrentUser\(\), "task"\)|任务分派可见集(仅 view, WithinGroup 集团收口)
score|view|resolveModuleUsersWithinGroup\(SecurityUtils\.getCurrentUser\(\), "score"\)|学分录入/排行可见集(仅 view, WithinGroup 集团收口)
system|view|resolveModuleUsersWithinGroup\(SecurityUtils\.getCurrentUser\(\), "system"\)|系统日志可见集(仅 view, admin 全集团; WithinGroup 集团收口)'

# ① 源码消费字面量 ⊆ 枚举
# 【N6 2026-11-11 修复】链式 .module()/.action() 之外, resolveModuleUsers/WithinGroup 的
# 位置参数形态(含三参 module+action)此前完全不可见——task/score/system/incentive 四模块
# 的消费点对"未声明检测"全部失效(评估反向实验实锤)。两路扫描并集。
miss=0
while IFS= read -r lit; do
  [ -n "$lit" ] || continue
  mod=$(printf '%s' "$lit" | grep -oE '"[a-z]+"' | tr -d '"' | head -1)
  grep -q "^$mod|" <<<"$AXIS" || { echo "  [未声明模块] .$lit"; miss=$((miss+1)); }
done < <( { grep -rhoE '\.module\("[a-z]+"\)' "$SRC" --include="*.java" | grep -vE "LogAspect" || true; } )
while IFS= read -r lit; do
  [ -n "$lit" ] || continue
  act=$(printf '%s' "$lit" | grep -oE '"[a-z_]+"' | tr -d '"' | head -1)
  grep -qE "^[a-z]+\|$act\|" <<<"$AXIS" || { echo "  [未声明动作] .$lit"; miss=$((miss+1)); }
done < <( { grep -rhoE '\.action\("[a-z_]+"\)' "$SRC" --include="*.java" | grep -vE "LogAspect" || true; } )
# 位置参数形态: resolveModuleUsers[WithinGroup](x, "mod"[, "action"]) —— action 缺省按 view 语义
# 【CI 可移植性 2026-11-12】三处 sed -E 全部 grep -oE 化(BSD/GNU 对该 ERE 的 ")" 解析分歧)
while IFS='|' read -r callmod callact; do
  [ -n "$callmod" ] || continue
  act="${callact:-view}"
  grep -q "^$callmod|$act|" <<<"$AXIS" || { echo "  [未声明消费] resolveModuleUsers*(…, \"$callmod\"${callact:+, \"$callact\"})"; miss=$((miss+1)); }
done < <( { grep -rhoE 'resolveModuleUsers(WithinGroup)?\([^,]+, "[a-z]+"(, "[a-z_]+")?' "$SRC" --include="*.java" \
  | { grep -oE '"[a-z_]+"(, "[a-z_]+")?' || true; } | awk '{gsub(/"/,""); n=split($0,a,", "); print a[1] "|" (n>1?a[2]:"")}' || true; } )
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

# ④ 【T7 2026-11-12】Java 写入校验集双向对账: RolePermScopeService.CONSUMED_AXIS 必须与 AXIS 同集
#   (写入面 400 校验依赖它; 两处漂移=活档被误拒炸保存 / 死档被放行)——不允许同一真值两处手写而不对账。
SVC="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/RolePermScopeService.java"
# 【CI 可移植性修复 2026-11-12】原 sed 范围地址在 GNU sed 报 Unmatched )(BSD 容忍)——
# 改 awk 提取 CONSUMED_AXIS 集合段(到行首右括号止), 双平台一致。
java_axis=$(awk '/CONSUMED_AXIS = java\.util\.Set\.of\(/{f=1} f{print} f&&/^\s*\);/{exit}' "$SVC" | grep -oE '"[a-z]+\|[a-z_]+"' | tr -d '"' | sort)
shell_axis=$(grep -oE "^[a-z]+\|[a-z_]+" <<<"$AXIS" | sort)
if [ "$java_axis" != "$shell_axis" ]; then
  echo "  [Java集≠Shell集] 差异:"; diff <(echo "$shell_axis") <(echo "$java_axis") || true
  fail "CONSUMED_AXIS 与动作轴声明表漂移——写入校验与消费面不同源"
fi
jcnt=$(echo "$java_axis" | grep -c .)

echo "PASS: 配置面=消费面 对账($cnt 档位: customer×4 / file×3 / approval+reminder+sea 仅 view; statistics:view 数据范围档已退役(WP1.5 锁定决策#7, 统计走 customer:view 单一源); Java写入校验集 $jcnt 档对账一致)"
