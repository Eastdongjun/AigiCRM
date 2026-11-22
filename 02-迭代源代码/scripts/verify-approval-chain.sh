#!/usr/bin/env bash
# 【全系统测试矩阵·06 审批链域】审批链运行时回归(2026-11-22)
# 口径源: 全系统测试矩阵模板 06 表 + ApprovalService 真实实现(提交建链->串行流转->终态守卫->业务回写)。
# 核心断言(与矩阵 06 表列一一对应):
#   AC-01 提交建链: 两阶段配置实例化 nodeOrder1(pending)/2(waiting), currentNode=阶段一
#   AC-02 越权/未到阶段: 非链上人与 waiting 阶段审批人操作 -> 统一文案拒绝(404 防探测)
#   AC-03 串行流转: 阶段一通过 -> 阶段二激活, 单据仍 reviewing, currentNode=阶段二
#   AC-04 重复/竞争: 已处理者再操作 -> 拒绝
#   AC-05 整单通过+数据比对(09联动): approved + 客户 approved_extend_days +30 + 延期事件落库(操作人=批准人)
#   AC-06 终态守卫: 终态后再 reject -> 拒绝(防反向翻转)
#   AC-07 驳回链路: 整单 rejected + 意见落库 + 客户天数不变
#   AC-08 自审闸门(approve侧): 申请人自批 -> 拒绝
#   AC-09 自审闸门(提交侧): 审批人全为本人 -> 拒绝提交
#   AC-10 类型白名单: 未知类型 -> 400
#   AC-11 日志联动(08联动): 审批动作是否留痕(不落=FAIL,如实登记)
# 夹具: pmxa_ 前缀用户/角色/客户 + extend 两阶段配置(active_extend 原为 0, 零干扰), 退出清理。
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-approval-chain.sh
# 依赖: ssh 别名 aigicrm-test 免密; 服务器 ~/aigicrm/.env 提供 DB_PASSWORD。
set -uo pipefail
BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
API_USER="${AIGI_TEST_USER:-admin}"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS}"
PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

db() {
  printf 'SET NAMES utf8mb4;\n%s\n' "$1" \
    | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' 2>/dev/null
}
login_token() {
  curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("token") or "")'
}
# POST 动作并输出 "HTTP码 消息" (message 优先, 无则 code)
act() { # $1=token $2=路径 $3=body(json或空)
  curl -s -m 20 -X POST "$BASE$2" -H "Authorization: Bearer $1" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} -w '\n%{http_code}' | python3 -c 'import json,sys
lines=sys.stdin.read().rsplit("\n",1); body,code=lines[0],lines[1]
try: m=json.loads(body).get("message") or ""
except Exception: m=body[:80]
print(code, m)'
}

TS="$(date +%s)"
HASH=$(PM_PW="PMxa$TS" python3 -c 'import bcrypt,os; print(bcrypt.hashpw(os.environ["PM_PW"].encode(), bcrypt.gensalt(10, prefix=b"2a")).decode())')
TMP_PW="PMxa$TS"
U_APP="pmxa_app$TS"; U_A1="pmxa_a1$TS"; U_A2="pmxa_a2$TS"; U_INTR="pmxa_intr$TS"
ROLE_CODE="pmxa_role$TS"

cleanup() {
  db "DELETE ar FROM biz_approval_node ar WHERE ar.approval_id IN (SELECT id FROM biz_approval WHERE applicant_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxa_%'));
      DELETE FROM biz_approval WHERE applicant_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxa_%');
      DELETE FROM biz_customer_event WHERE customer_id IN (SELECT id FROM biz_customer WHERE customer_name LIKE 'pmxa-c-%');
      DELETE FROM biz_customer WHERE customer_name LIKE 'pmxa-c-%';
      DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'pmxa_%');
      DELETE FROM sys_user WHERE username LIKE 'pmxa_%';
      DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='$ROLE_CODE');
      DELETE FROM sys_role WHERE role_code='$ROLE_CODE';
      DELETE FROM biz_approval_config WHERE flow_name LIKE 'pmxa%';" >/dev/null
}
trap cleanup EXIT
cleanup

G1_DEPT=$(db "SELECT id FROM sys_dept WHERE group_id=1 AND parent_id=0 AND status=1 ORDER BY id LIMIT 1" | tail -1)
# 夹具: 角色(view/approve/reject 菜单 18/45/46) + 四用户(app=发起人, a1/a2=两阶段审批人, intr=非链上人)
db "INSERT INTO sys_role (group_id, role_name, role_code, description, data_scope, status) VALUES (1,'审批链回归','$ROLE_CODE','矩阵06域夹具,即删','self',1);" >/dev/null
RID=$(db "SELECT id FROM sys_role WHERE role_code='$ROLE_CODE'" | tail -1)
db "INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($RID,18),($RID,45),($RID,46);" >/dev/null
for U in "$U_APP" "$U_A1" "$U_A2" "$U_INTR"; do
  db "INSERT INTO sys_user (username, password, real_name, status, pwd_reset_required, org_id, dept_id) VALUES ('$U','$HASH','$U',1,0,$G1_DEPT,$G1_DEPT);
      INSERT INTO sys_user_role (user_id, role_id) VALUES ((SELECT id FROM sys_user WHERE username='$U'), $RID);" >/dev/null
done
ID_APP=$(db "SELECT id FROM sys_user WHERE username='$U_APP'" | tail -1)
ID_A1=$(db "SELECT id FROM sys_user WHERE username='$U_A1'" | tail -1)
ID_A2=$(db "SELECT id FROM sys_user WHERE username='$U_A2'" | tail -1)
ID_INTR=$(db "SELECT id FROM sys_user WHERE username='$U_INTR'" | tail -1)
[ -n "$ID_APP" ] && [ -n "$ID_A1" ] && [ -n "$ID_A2" ] && [ -n "$ID_INTR" ] || { bad "夹具用户创建失败"; echo "==> 审批链回归: PASS=$PASS FAIL=$FAIL"; exit 1; }
# 客户(normal 私海, 可发起延期) + 两阶段 extend 配置(原 active_extend=0 零干扰)
db "INSERT INTO biz_customer (customer_name, customer_code, tenant_id, owner_id, status, deleted, created_at) VALUES ('pmxa-c-$TS','PMXA-$TS',1,$ID_APP,'normal',0,NOW());
    INSERT INTO biz_approval_config (flow_name, flow_type, approver_type, approver_ids, sign_mode, sort_order, status, tenant_id) VALUES
      ('pmxa阶段一','extend','USER','[$ID_A1]','ANY',1,1,1),
      ('pmxa阶段二','extend','USER','[$ID_A2]','ANY',2,1,1);" >/dev/null
CID=$(db "SELECT id FROM biz_customer WHERE customer_name='pmxa-c-$TS'" | tail -1)
[ -n "$CID" ] || { bad "夹具客户创建失败"; echo "==> 审批链回归: PASS=$PASS FAIL=$FAIL"; exit 1; }
DAYS_BEFORE=$(db "SELECT IFNULL(approved_extend_days,0) FROM biz_customer WHERE id=$CID" | tail -1)

latest_approval() { db "SELECT id FROM biz_approval WHERE applicant_id=$ID_APP ORDER BY id DESC LIMIT 1" | tail -1; }
node_state() { # $1=approval_id $2=approver_id -> "order:status" 列表
  db "SELECT CONCAT(node_order,':',status) FROM biz_approval_node WHERE approval_id=$1 ORDER BY node_order" | tail -n +2 | paste -sd' ' -
}

T_APP=$(login_token "$U_APP" "$TMP_PW"); T_A1=$(login_token "$U_A1" "$TMP_PW")
T_A2=$(login_token "$U_A2" "$TMP_PW"); T_INTR=$(login_token "$U_INTR" "$TMP_PW")
[ -n "$T_APP" ] && [ -n "$T_A1" ] && [ -n "$T_A2" ] && [ -n "$T_INTR" ] && ok "夹具四账号登录" || bad "夹具登录失败"

SUBMIT_BODY="{\"type\":\"extend\",\"customerId\":\"$CID\",\"extraData\":\"{\\\"extendDays\\\":30}\"}"

# ---- AC-01 提交建链 ----
R=$(act "$T_APP" /api/approval/submit "$SUBMIT_BODY")
if [ "${R%% *}" = "200" ]; then ok "AC-01 提交: 200"; else bad "AC-01 提交失败: $R"; fi
A1_ID=$(latest_approval)
ST=$(db "SELECT CONCAT(status,'|',current_node) FROM biz_approval WHERE id=$A1_ID" | tail -1)
NS=$(node_state "$A1_ID")
[ "$ST" = "reviewing|pmxa阶段一" ] && ok "AC-01 建链: reviewing + currentNode=阶段一 ($ST)" || bad "AC-01 状态/当前节点: $ST (期望 reviewing|pmxa阶段一)"
[ "$NS" = "1:pending 2:waiting" ] && ok "AC-01 两阶段实例化: 1:pending 2:waiting" || bad "AC-01 节点形态: '$NS' (期望 '1:pending 2:waiting')"

# ---- AC-02 越权 + 未到阶段 ----
R=$(act "$T_INTR" "/api/approval/$A1_ID/approve")
case "$R" in *"资源不存在或无权访问"*) ok "AC-02 越权(非链上人): 统一文案拒绝";; *) bad "AC-02 越权未按预期拒绝: $R";; esac
R=$(act "$T_A2" "/api/approval/$A1_ID/approve")
case "$R" in *"资源不存在或无权访问"*) ok "AC-02 未到阶段(waiting 审批人): 拒绝";; *) bad "AC-02 waiting 阶段操作未拒: $R";; esac

# ---- AC-03 串行流转 ----
R=$(act "$T_A1" "/api/approval/$A1_ID/approve" '{"comment":"阶段一通过"}')
[ "${R%% *}" = "200" ] && ok "AC-03 阶段一审批: 200" || bad "AC-03 阶段一失败: $R"
ST=$(db "SELECT CONCAT(status,'|',current_node) FROM biz_approval WHERE id=$A1_ID" | tail -1)
NS=$(node_state "$A1_ID")
[ "$ST" = "reviewing|pmxa阶段二" ] && ok "AC-03 流转: 仍 reviewing + currentNode=阶段二" || bad "AC-03 流转状态: $ST"
[ "$NS" = "1:approved 2:pending" ] && ok "AC-03 阶段激活: 1:approved 2:pending" || bad "AC-03 节点形态: '$NS' (期望 '1:approved 2:pending')"

# ---- AC-04 重复/竞争 ----
R=$(act "$T_A1" "/api/approval/$A1_ID/approve")
case "$R" in *"资源不存在或无权访问"*|*"该审批已处理"*) ok "AC-04 已处理者再操作: 拒绝($R)";; *) bad "AC-04 重复操作未拒: $R";; esac

# ---- AC-05 整单通过 + 数据比对(09 联动) ----
R=$(act "$T_A2" "/api/approval/$A1_ID/approve" '{"comment":"阶段二通过"}')
[ "${R%% *}" = "200" ] && ok "AC-05 阶段二审批: 200" || bad "AC-05 阶段二失败: $R"
ST=$(db "SELECT status FROM biz_approval WHERE id=$A1_ID" | tail -1)
[ "$ST" = "approved" ] && ok "AC-05 整单: approved" || bad "AC-05 整单状态: $ST"
DAYS_AFTER=$(db "SELECT IFNULL(approved_extend_days,0) FROM biz_customer WHERE id=$CID" | tail -1)
EXPECT=$((DAYS_BEFORE + 30))
[ "$DAYS_AFTER" = "$EXPECT" ] && ok "AC-05 数据比对: approved_extend_days $DAYS_BEFORE->$DAYS_AFTER (+30 精确)" || bad "AC-05 天数回写: $DAYS_BEFORE->$DAYS_AFTER (期望 $EXPECT)"
EV=$(db "SELECT COUNT(*) FROM biz_customer_event WHERE customer_id=$CID AND event_type='extend'" | tail -1)
OP=$(db "SELECT IFNULL(created_by,-1) FROM biz_customer_event WHERE customer_id=$CID AND event_type='extend' ORDER BY id DESC LIMIT 1" | tail -1)
[ "$EV" -ge 1 ] && [ "$OP" = "$ID_A2" ] && ok "AC-05 延期事件: 落库且操作人=最终批准人(A2)" || bad "AC-05 延期事件: count=$EV created_by=$OP (期望≥1 且=$ID_A2)"

# ---- AC-06 终态守卫 ----
R=$(act "$T_A2" "/api/approval/$A1_ID/reject" '{"comment":"事后驳回"}')
case "$R" in *"资源不存在或无权访问"*) ok "AC-06 终态守卫: approved 后再 reject 被拒";; *) bad "AC-06 终态可翻转!: $R";; esac

# ---- AC-07 驳回链路 ----
R=$(act "$T_APP" /api/approval/submit "$SUBMIT_BODY")
[ "${R%% *}" = "200" ] && ok "AC-07 第二单提交: 200" || bad "AC-07 第二单提交失败: $R"
A2_ID=$(latest_approval)
R=$(act "$T_A1" "/api/approval/$A2_ID/reject" '{"comment":"材料不足驳回"}')
[ "${R%% *}" = "200" ] && ok "AC-07 阶段一驳回: 200" || bad "AC-07 驳回失败: $R"
ST=$(db "SELECT status FROM biz_approval WHERE id=$A2_ID" | tail -1)
CM=$(db "SELECT IFNULL(comment,'') FROM biz_approval_node WHERE approval_id=$A2_ID AND action='reject' ORDER BY id DESC LIMIT 1" | tail -1)
DAYS_R=$(db "SELECT IFNULL(approved_extend_days,0) FROM biz_customer WHERE id=$CID" | tail -1)
[ "$ST" = "rejected" ] && ok "AC-07 整单: rejected" || bad "AC-07 驳回后状态: $ST"
case "$CM" in *材料不足驳回*) ok "AC-07 驳回意见落库";; *) bad "AC-07 意见未落: '$CM'";; esac
[ "$DAYS_R" = "$DAYS_AFTER" ] && ok "AC-07 客户数据不变(驳回不回写): 天数=$DAYS_R" || bad "AC-07 驳回竟改天数: $DAYS_AFTER->$DAYS_R"

# ---- AC-08 自审闸门(approve 侧) ----
R=$(act "$T_APP" /api/approval/submit "$SUBMIT_BODY")
[ "${R%% *}" = "200" ] && ok "AC-08 第三单提交: 200" || bad "AC-08 第三单提交失败: $R"
A3_ID=$(latest_approval)
R=$(act "$T_APP" "/api/approval/$A3_ID/approve")
# 语义说明: 申请人不在节点上时, approve() 节点查找先于自审闸门 → 走统一 404 防探测文案;
# 「申请人不能审批自己」仅在申请人恰为某节点审批人(混合链)时出现。两种都算正确拒绝。
case "$R" in
  *"申请人不能审批自己"*|*"资源不存在或无权访问"*) ok "AC-08 自审批(approve侧): 拦截($R)";;
  *) bad "AC-08 自审批未拦!: $R";;
esac
# 处置第三单(驳回), 否则 extend 在途防重守卫会挡住 AC-09 的提交
act "$T_A1" "/api/approval/$A3_ID/reject" '{"comment":"AC-08 夹具处置"}' >/dev/null

# ---- AC-09 自审闸门(提交侧): 全阶段审批人=申请人才拒单; 混合链(本人+他人)按设计放行 ----
db "UPDATE biz_approval_config SET approver_ids='[$ID_APP]' WHERE flow_type='extend' AND flow_name LIKE 'pmxa%';" >/dev/null
R=$(act "$T_APP" /api/approval/submit "$SUBMIT_BODY")
case "$R" in *"审批人全部是申请人本人"*) ok "AC-09 自审批(提交侧): 全本人链拒绝提交";; *) bad "AC-09 提交侧未拦: $R";; esac
db "UPDATE biz_approval_config SET approver_ids='[$ID_A1]' WHERE flow_name='pmxa阶段一';" >/dev/null
db "UPDATE biz_approval_config SET approver_ids='[$ID_A2]' WHERE flow_name='pmxa阶段二';" >/dev/null

# ---- AC-10 类型白名单 ----
R=$(act "$T_APP" /api/approval/submit '{"type":"pmxa_fake","customerId":null}')
case "$R" in *"不支持的审批类型"*) ok "AC-10 未知类型: 400 白名单拦截";; *) bad "AC-10 未知类型未拦: $R";; esac

# ---- AC-11 日志联动(08 联动; 不留痕=FAIL 如实登记) ----
LOG1=$(db "SELECT COUNT(*) FROM sys_audit_log WHERE created_at > NOW() - INTERVAL 30 MINUTE AND (actor_username LIKE 'pmxa%' OR detail LIKE '%pmxa%')" | tail -1)
LOG2=$(db "SELECT COUNT(*) FROM sys_log WHERE created_at > NOW() - INTERVAL 30 MINUTE AND (operation LIKE '%审批%' OR operation LIKE '%approval%' OR params LIKE '%pmxa%')" 2>/dev/null | tail -1)
LOG2="${LOG2:-0}"
if [ "${LOG1:-0}" -ge 1 ] || [ "${LOG2:-0}" -ge 1 ]; then
  ok "AC-11 日志联动: 审批动作留痕(audit=$LOG1 log=$LOG2)"
else
  bad "AC-11 日志联动: approve/reject 无任何日志/审计留痕(audit=0 log=0)——矩阵08域缺口,登记问题单"
fi

echo "==> 审批链回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]