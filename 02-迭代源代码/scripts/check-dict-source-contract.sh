#!/usr/bin/env bash
# ============================================================
# 字典来源契约（2026-10-29 立门禁）
# 背景: 审计发现"系统字典建了但业务端硬编码不用"——dev_type 硬编码缺第四项、行业硬编码缺
#       「新行业」、core_indicator_02~06 五个字典全库零引用、字典与代码文案各改各的。
# 规则:
#   1) 字典清单对账: 每个 sys_dict_type 字典类型, 业务代码(PC/MP/后端)必须有引用;
#      retired 状态除外(须有停用迁移落档)。新建字典必须同步登记本清单, 否则孤儿即 FAIL。
#   2) 接线钉: 已接字典的关键调用点必须存在(防"重构时顺手删了字典调用")。
#   3) 防复活钉: 已修复的硬编码选项源禁止回潮(必须走 getDictData)。
# 退出码: 0=通过; 1=发现孤儿字典/接线缺失/硬编码回潮
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI="$ROOT/aigicrm-ui/src"
MP="$ROOT/aigicrm-miniprogram"
BE="$ROOT/aigicrm-backend/src/main/java"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"

fail() { echo "FAIL: $*" >&2; exit 1; }

# —— 字典清单(与生产 sys_dict_type 对账; 新建/停用字典必须同步改这里) ——
# 格式: code|名称|状态  状态: wired=被业务引用 / retired=已停用(须有停用迁移)
DICTS=(
  "dev_type|开发类型|wired"
  "score_category|学分大类|wired"
  "score_product|学分产品|wired"
  "score_range|学分区间|wired"
  "relationship_depth|客情关系深度|wired"
  "core_indicator_02|核心指标02(与relationship_depth重复)|retired"
  "core_indicator_03|核心指标03-渠道编码|wired"
  "core_indicator_04|核心指标04-基层覆盖|wired"
  "core_indicator_05|核心指标05-产品品类|wired"
  "core_indicator_06|核心指标06-项目类型|wired"
  "industry|行业类型|wired"
)

echo "=== [字典对账] 每个字典类型必须有业务引用(retired 须有停用迁移) ==="
for entry in "${DICTS[@]}"; do
  code="${entry%%|*}"; rest="${entry#*|}"; status="${rest##*|}"
  refs=$(grep -rl --include='*.js' --include='*.vue' --include='*.java' "'$code'" \
    "$UI" "$MP/pages" "$MP/utils" "$MP/api" "$BE" 2>/dev/null | grep -v node_modules | wc -l | tr -d ' ' || true)
  if [ "$status" = "retired" ]; then
    # retired: 必须存在引用该 code 的停用迁移(防"清单说退役了但迁移没写")
    grep -rql "$code" "$MIG" 2>/dev/null \
      || fail "字典 $code 标记 retired 但迁移目录找不到停用迁移"
    echo "PASS(retired): $code"
  else
    [ "$refs" -ge 1 ] || fail "字典 $code 在业务代码(PC/MP/后端)零引用——要么接线, 要么停用并登记本清单"
    echo "PASS(wired x$refs): $code"
  fi
done

echo "=== [接线钉] 已接字典的关键调用点必须存在 ==="
require_contains() {
  local needle="$1" file="$2" msg="$3"
  grep -qF "$needle" "$file" || fail "$msg: $file 缺 $needle"
}
# PC: 开发类型/行业下拉=字典驱动(激励查询)
require_contains "getDictData('dev_type')" "$UI/views/incentive/search.vue" "PC激励查询开发类型未走字典"
require_contains "getDictData('industry')" "$UI/views/incentive/search.vue" "PC激励查询行业未走字典"
# PC: 指标 03~06 选项=字典接线(表单/详情/排行共用)
require_contains "fetchIndicatorDictOptions" "$UI/utils/customer-fields.js" "PC指标选项缺字典接线函数"
require_contains "fetchIndicatorDictOptions(getDictData)" "$UI/composables/useCustomerForm.js" "PC客户表单未拉指标字典"
require_contains "fetchIndicatorDictOptions(getDictData)" "$UI/composables/useCustomerDetail.js" "PC客户详情未拉指标字典"
# MP: 激励两页行业=字典驱动
require_contains "getDictData('industry')" "$MP/pages/incentive/home/index.js" "MP轻量页行业未走字典"
require_contains "getDictData('industry')" "$MP/pages/incentive/index.js" "MP完整版行业未走字典"
# MP: 指标选项=字典接线 + devType 兜底四项全量
require_contains "fetchIndicatorDictOptions" "$MP/pages/customer/shared.js" "MP指标选项缺字典接线函数"
require_contains "fetchIndicatorDictOptions(getDictData)" "$MP/pages/customer/detail/index.js" "MP客户详情未拉指标字典"
require_contains "fetchIndicatorDictOptions(getDictData)" "$MP/pages/customer/form/index.js" "MP客户表单未拉指标字典"
require_contains "'新建改建扩建大修'" "$MP/pages/customer/form/index.js" "MP开发类型兜底缺第四类"

echo "=== [接线钉·产品目录归树 2026-10-29] 产品目录唯一来源=分类树(系统设置→激励类别管理) ==="
# PC: 三处消费方必须走树派生(buildProductCatsFromTree), 常量只作兜底
require_contains "buildProductCatsFromTree" "$UI/views/incentive/product-catalog.js" "PC产品目录缺树派生函数"
require_contains "buildProductCatsFromTree" "$UI/views/incentive/search.vue" "PC激励查询产品目录未走树派生"
require_contains "buildProductCatsFromTree" "$UI/composables/useIncentiveUpload.js" "PC上传弹窗产品目录未走树派生"
# MP: 上传页树派生 + 两列表页产品清单接树(trial 分支锚)
require_contains "deriveProductCatalog" "$MP/pages/incentive/upload/index.js" "MP上传页产品目录未走树派生"
require_contains "'trial'" "$MP/pages/incentive/home/index.js" "MP轻量页产品清单未接树派生"
require_contains "'trial'" "$MP/pages/incentive/index.js" "MP完整版产品清单未接树派生"
# 唯一入口成立的前提: 激励类别管理页能渲染/维护第三级
require_contains "cat-grand-list" "$UI/views/system/CategoryManager.vue" "激励类别管理缺三级渲染(产品小类不可维护)"
# 记录分类只取前两级(第三级=产品目录不进记录分类; 左侧子项取值错位事故 2026-10-29)
require_contains "items: null" "$UI/composables/useIncentiveUpload.js" "PC左侧子项取到三级(记录分类必须停二级)"
require_contains "level < 2" "$MP/pages/incentive/index.js" "MP完整版分类浏览缺两级截断"
require_contains "depth < 2" "$MP/pages/incentive/upload/index.js" "MP上传页分类缺两级截断"

echo "=== [防复活钉] 已修复的硬编码选项源禁止回潮 ==="
reject_regex() {
  local pattern="$1" file="$2" msg="$3"
  ! grep -Eq "$pattern" "$file" || fail "$msg: $file"
}
# PC 激励查询: 选项禁止回到静态硬编码数组(兜底常量 INDUSTRY_FALLBACK/DEV_TYPE_FALLBACK 除外)
reject_regex "const industries\s*=\s*\[" "$UI/views/incentive/search.vue" "行业选项回潮硬编码(应 getDictData('industry'))"
reject_regex "const devTypes\s*=\s*\[" "$UI/views/incentive/search.vue" "开发类型回潮硬编码(应 getDictData('dev_type'))"
# PC 上传 composable: 孙级节点禁止再被当记录分类消费(产品目录派生在 product-catalog.js 内部, 不受此钉)
reject_regex "grandchildren" "$UI/composables/useIncentiveUpload.js" "PC又把孙级当记录分类(第三级只属于产品目录)"

echo "=== [术语钉·学分改名 2026-11-01] score 模块统一学名分(菜单/页面/导出), 周期只留全年+左列冻结 ==="
require_contains "学分录入" "$UI/views/score/entry.vue" "PC学分录入页未更名"
require_contains "查看排行" "$UI/views/score/entry.vue" "PC录入页缺查看排行直达入口"
require_contains "goRanking()" "$UI/views/score/entry.vue" "保存成功后未自动跳转排行(录入后直接显示排行)"
require_contains 'year.value}全年' "$UI/views/score/entry.vue" "录入周期未收敛为全年"
require_contains "position:sticky" "$UI/views/score/entry.vue" "录入表缺左列冻结"
require_contains "学分排行" "$UI/views/score/ranking.vue" "PC学分排行页未更名"
require_contains "option.label.endsWith('全年')" "$UI/views/score/ranking.vue" "排行周期选项未收敛为全年"
require_contains "route.query.period" "$UI/views/score/ranking.vue" "排行缺 ?period= 预选支持"
require_contains "'学分排行'" "$UI/router/index.js" "路由标题未更名"
require_contains "学分管理" "$UI/composables/usePermissionData.js" "权限分组未更名"
require_contains "人员学分 Top5" "$MP/pages/statistics/index.wxml" "MP统计页Top5未更名"
require_contains "学分录入.xlsx" "$BE/com/aigicrm/module/score/service/ScoreService.java" "后端导出文件名未更名"
# 防复活: score 模块旧称与已退役的半年口径禁止回潮(历史注释行文避开 上半年/下半年 字样)
! grep -Fq '积分录入' "$UI/views/score/entry.vue" || fail "score 页面积分旧称回潮(entry)"
! grep -Fq '积分排行' "$UI/views/score/ranking.vue" || fail "score 页面积分旧称回潮(ranking)"
! grep -Fq '人员积分' "$MP/pages/statistics/index.wxml" || fail "MP统计页积分旧称回潮"
! grep -Eq '上半年|下半年' "$UI/views/score/entry.vue" || fail "学分录入半年口径回潮(应只留全年)"
! grep -Eq '上半年|下半年' "$UI/views/score/ranking.vue" || fail "学分排行半年口径回潮(应只留全年)"

echo 'PASS: 字典来源契约通过(清单对账+接线钉+防复活钉)。'
