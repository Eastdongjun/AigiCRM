#!/usr/bin/env bash
# Contract for 保护期口径批(2026-11-02 用户裁决A/B/C/D):
#   A 一年=365天(原360) | B 捞取后短保护180天(claim_date独立字段,注册日不再被覆写)
#   C adjust_date语义=捞取到期日(仅捞取客户有值) | D 客户生命线事件
# 改动这些口径必须同步改本钉(欠账七式①: 改默认值→契约钉)。

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CS="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java"
SEA="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/sea/service/SeaService.java"
CTRL="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java"
EVENTSVC="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerEventService.java"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.02.4__claim_date_and_protection_365.sql"
MIG_EVENTS="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.02.5__customer_lifecycle_events.sql"
ADD_VUE="$ROOT/aigicrm-ui/src/views/customer/add.vue"
ADDR_VUE="$ROOT/aigicrm-ui/src/views/customer/AddressSection.vue"
MAIN_TABLE="$ROOT/aigicrm-ui/src/views/customer/MainTable.vue"
USE_LIST="$ROOT/aigicrm-ui/src/composables/useCustomerList.js"
DETAIL_VUE="$ROOT/aigicrm-ui/src/views/customer/detail.vue"
EVENTS_TAB="$ROOT/aigicrm-ui/src/views/customer/detail/EventsTab.vue"
CUSTOMER_API="$ROOT/aigicrm-ui/src/api/customer.js"

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_file() { [ -f "$1" ] || fail "Missing file: $1"; }
assert_contains() { grep -Fq "$3" "$2" || fail "Missing '$3' in $2"; }
assert_not_contains() { ! grep -Fq "$3" "$2" || fail "Unexpected '$3' in $2 (口径回归)"; }

for f in "$CS" "$SEA" "$CTRL" "$EVENTSVC" "$MIG" "$MIG_EVENTS" "$ADD_VUE" "$ADDR_VUE" \
         "$MAIN_TABLE" "$USE_LIST" "$DETAIL_VUE" "$EVENTS_TAB" "$CUSTOMER_API"; do
  assert_file "$f"
done

# ① 裁决A: 默认保护 365(后端不得残留 360 默认值; 测试文件不在本钉范围)
assert_contains protection_days_365 "$CS" 'getConfigInt("customer.protection.days", 365)'
assert_not_contains protection_days_360 "$CS" 'getConfigInt("customer.protection.days", 360)'

# ② 裁决B: 捞取短保护=捞取线 claim+adjust.days(180); 注册日不再被覆写; 捞取日期独立字段
assert_contains claim_line "$CS" 'claim.plusDays(adjustDays)'
assert_contains claim_reset_entry "$CS" 'public void applyClaimReset(BizCustomer customer, LocalDate claimDate)'
assert_contains claim_guard "$CS" 'customer.getClaimDate() != null && candidateDate.isBefore(customer.getClaimDate())'
assert_not_contains sea_overwrite_register "$SEA" 'customer.setRegisterDate'
assert_contains claim_written "$SEA" 'applyClaimReset'
assert_not_contains sea_dead_config_helper "$SEA" 'private int getConfigInt'

# ③ 裁决C: 捞取到期日=捞取日+180, 仅捞取客户有值(前端列头不得回退"调整日")
assert_contains pc_label_list "$USE_LIST" "label: '捞取到期日'"
assert_contains pc_label_table "$MAIN_TABLE" 'label="捞取到期日"'
assert_not_contains pc_label_old "$MAIN_TABLE" 'label="调整日"'
assert_contains claim_date_col "$USE_LIST" "label: '捞取日期'"

# ④ 裁决D: 生命线(事件类型白名单+端点+详情页签)
assert_contains event_types "$EVENTSVC" 'TYPE_TRANSFERRED = "transferred"'
assert_contains events_endpoint "$CTRL" '@GetMapping("/{id}/events")'
assert_contains events_tab_api "$CUSTOMER_API" 'getCustomerEvents'
assert_contains events_tab_mount "$DETAIL_VUE" 'name="events"'
assert_contains events_tab_file "$EVENTS_TAB" 'TYPE_LABELS'

# ⑤ 迁移钉: claim_date 列+配置 365+存量重算(三线)——不含反引号的稳定子串
assert_contains mig_claim_col "$MIG" 'ADD COLUMN'
assert_contains mig_cfg_365 "$MIG" 'customer.protection.days'
assert_contains mig_cfg_365_val "$MIG" "'360'"
assert_contains mig_recalc "$MIG" 'INTERVAL 365 DAY'
assert_contains mig_claim_backfill "$MIG" 'biz_sea_pool'
assert_contains mig_events_table "$MIG_EVENTS" 'biz_customer_event'

# ⑥ 前端口径文案: 一年=365(不得回退 360 天文案)
assert_contains add_hint_365 "$ADD_VUE" '+ 365 天'
assert_not_contains add_hint_360 "$ADD_VUE" '+ 360 天'
assert_contains addr_claim_date "$ADDR_VUE" '捞取日期'

echo "PASS: customer-protection-365 (A 365天 / B 捞取180+claim_date / C 捞取到期日 / D 生命线)"
