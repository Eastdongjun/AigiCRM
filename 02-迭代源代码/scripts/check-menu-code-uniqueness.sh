#!/usr/bin/env bash
# 【T5 2026-11-12】菜单行与权限码 1:1 契约(一码一行)
#
# 背景: 同一 permission 在 sys_menu 多行(页面行/按钮行混挂/同型多页面行) → 勾不同行效果不同、
#   权限字典合并显示, 管理员无从分辨("勾了看不到/没勾也能进"歧义源之一)。
# 本契约两层:
#   ① 静态层(防新增): 扫描 db/migration 全链中 sys_menu 插入值行的 (menu_type, path, permission)
#      三元组——按码聚合去重后, 断言"menu_type=2 页面行 ≤1"且"2/3 混合仅限白名单"。
#      局限(如实声明): 静态正则不执行 SQL, UPDATE/DELETE 改写不回放——最终态对账走
#      scripts/replay-empty-db.sh(需 Docker, 本机 daemon 未运行时以静态层为准)。
#   ② 白名单层: 存量已知多行登记于此, 违例=白名单外新增。白名单外出现 → 红。
# 白名单(存量, 待产品裁决合并/拆码, 台账见 docs/REQUIREMENTS.md):
#   customer:add        = 页面+按钮行(历史) —— 页面+按钮 2/3 混合
#   incentive:stats:view = 页面 + 按钮行 —— 同上
#   system:config:view  = 3 页面行(配置组/字典/系统设置) —— 功能歧义型, 待产品裁决合并(真违例)
#   多页面行=同功能域多路由共享码(列表/详情/分组页等), 有意设计非违例:
#   statistics:view score:view customer:view incentive:view sea:view file:view
#   system:user:view/system:dept:view = 页面行 + visible=0 隐藏字典登记行(权限树可勾/侧栏不显)
#   system:log:view = 时序重登记(V2026.11.05.2 合并进设置→05.3 重登), 静态层两次插入运行态唯一
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"
fail() { echo "FAIL: $1" >&2; exit 1; }

# 【P1 修复 2026-11-12】负例自证通道(AIGI_NEGATIVE=1): 内存摘白名单一个存量多行码——
# 不动真源码/真白名单; 存量违例(如 system:user:view 多页面行)必须触发红, 证明检测器有效。
# 递归调用时显式清零 AIGI_NEGATIVE 防死循环; AIGI_NEG_STRIP 供递归实例从白名单摘码。
if [ "${AIGI_NEGATIVE:-0}" = "1" ]; then
  STRIP="system:user:view"
  OUT=$(AIGI_NEGATIVE=0 AIGI_NEG_STRIP="$STRIP" bash "$0" 2>&1 || true)
  if ! echo "$OUT" | grep -q "多页面行] $STRIP"; then
    fail "[负例自证] 摘除白名单($STRIP)后存量违例未红——检测器失效"
  fi
  echo "FAIL: [负例自证] $STRIP 摘白名单后存量多页面行违例正确抓红" >&2
  exit 1
fi

WHITELIST_23="customer:add incentive:stats:view"      # 2/3 混合白名单
WHITELIST_MULTI2="system:config:view statistics:view score:view customer:view incentive:view sea:view system:user:view system:dept:view system:log:view"  # 多页面行白名单(有意多路由/隐藏字典行/时序重登记; config:view 除外=待裁决)
# 【P1】负例递归实例: 从对应白名单内存摘码(不动真白名单源)
if [ -n "${AIGI_NEG_STRIP:-}" ]; then
  WHITELIST_MULTI2=" $WHITELIST_MULTI2 "; WHITELIST_MULTI2=${WHITELIST_MULTI2// $AIGI_NEG_STRIP / }
fi

# 抽取 (menu_type, perm, path) 三元组: 值行统一形态 "…, <2|3>, '<path>', '<component>', '<perm>', …"
# (type, perm, path) 三维去重——同码不同 path 的多页面行须保留(仅同码同 path 的防重种子折叠)
pairs=$(grep -hoE ",\s*([23]),\s*'[^']*',\s*'[^']*',\s*'([a-z][a-z0-9_-]*:[a-z0-9:_-]+)'," "$MIG"/*.sql 2>/dev/null \
  | sed -E "s/,[[:space:]]*([23]),[[:space:]]*'([^']*)',[[:space:]]*'[^']*',[[:space:]]*'([a-z][a-z0-9_-]*:[a-z0-9:_-]+)',/\1 \3 \2/" \
  | sort -u)

# 按码聚合: 页面行(type2)计数 与 是否含按钮行(type3)。pairs 每行 = "<type> <perm>"
viol=""
while read -r perm _extra; do
  [ -z "${perm:-}" ] && continue
  echo "$pairs" | awk -v p="$perm" '$2==p {print $1}' > /tmp/t5-types.txt
  t2=$(grep -c '^2$' /tmp/t5-types.txt || true)
  has3=$(grep -c '^3$' /tmp/t5-types.txt || true)
  if [ "$t2" -gt 1 ]; then
    echo " $WHITELIST_MULTI2 " | grep -q " $perm " || viol="$viol
  [多页面行] $perm: $t2 个 type=2 插入点(白名单外)"
  fi
  if [ "$t2" -ge 1 ] && [ "$has3" -ge 1 ]; then
    echo " $WHITELIST_23 " | grep -q " $perm " || viol="$viol
  [2/3混合] $perm: 页面行+按钮行并存(白名单外)"
  fi
done < <(echo "$pairs" | awk '{print $2}' | sort -u)

[ -z "$viol" ] || fail "菜单-权限码 1:1 契约违例(新增多行须先入白名单并在 REQUIREMENTS 记台账):$viol"

# 白名单码必须仍真实存在(白名单腐烂检测; pairs 行 = "<type> <perm> <path>")
for p in $WHITELIST_23 $WHITELIST_MULTI2; do
  echo "$pairs" | grep -qE "^[23] $p( |$)" || echo "WARN: 白名单码 $p 在迁移链中已无插入行——请从白名单移除"
done

echo "PASS: 菜单-权限码 1:1 契约(页面行唯一/混合白名单) 通过。"
