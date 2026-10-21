#!/bin/bash
# scripts/verify-role-org-sql.sh — 验证 SysDeptMapper 两个递归查询在真实 MySQL 上的行为
#   selectEffectiveGroupId / selectDescendantGroupIds (SysDeptMapper.java, P2(c) 角色-组织集团校验)
# 覆盖:
#   - Q1 有效集团 NULL 继承: NULL group 链解析出最近祖先集团(修复误伤的关键逻辑)
#   - Q2 子树跨集团检测: 锚点子树含其他集团节点时 selectDescendantGroupIds 返回该集团
#   - Q3/Q4 真实库语法+行为抽查(有数据时): 有效集团解析 / 子树集团集合
# 方式: seed CTE 合成数据(零副作用) + 真实 sys_dept 只读查询
# 用法:
#   verify-role-org-sql.sh <DB_HOST> <DB_USER> <DB_PASS> <DB_NAME> [选项]
#   省略参数时从环境变量读取: DB_HOST/DB_USER/DB_PASS/DB_NAME(同 check-permission-data.sh)
# 选项:
#   --remote            经 SSH + docker exec 在服务器容器内执行(默认容器 aigicrm-mysql)
#   --local             本机 mysql 客户端直连(需本机安装 mysql client)
#   --container NAME    指定远端容器名(仅 --remote)
#   缺省自动判定: 本机有 mysql 客户端 → local; 否则 → remote
# 退出码: 0=全部断言通过; 1=任一断言失败或执行失败
set -uo pipefail

usage() {
  awk 'NR > 1 { if ($0 ~ /^#/) { sub(/^# ?/, "", $0); print } else exit }' "$0"
}

DB_HOST="${DB_HOST:-}"; DB_USER="${DB_USER:-}"; DB_PASS="${DB_PASS:-}"; DB_NAME="${DB_NAME:-}"
MODE=auto; CONTAINER="${APP_SLUG:-aigicrm}-mysql"; POSN=0
while [ $# -gt 0 ]; do
  case "$1" in
    --remote) MODE=remote; shift ;;
    --local) MODE=local; shift ;;
    --container) CONTAINER="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*)
      echo "未知参数: $1" >&2; usage; exit 2 ;;
    *)
      POSN=$((POSN+1))
      case "$POSN" in
        1) DB_HOST=$1 ;; 2) DB_USER=$1 ;; 3) DB_PASS=$1 ;; 4) DB_NAME=$1 ;;
        *) echo "多余位置参数: $1" >&2; usage; exit 2 ;;
      esac
      shift ;;
  esac
done
[ -n "$DB_HOST" ] || { echo "错误: 缺少 DB_HOST" >&2; usage; exit 2; }
[ -n "$DB_NAME" ] || { echo "错误: 缺少 DB_NAME" >&2; usage; exit 2; }
[ -n "$CONTAINER" ] || { echo "错误: --container 需要容器名" >&2; exit 2; }

# ---- 执行模式(同 check-permission-data.sh) ----
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# RUN_CMD/SSH_FN are selected from validated local functions at runtime.
# shellcheck disable=SC2329
mysql_direct() { MYSQL_PWD="$DB_PASS" mysql -h"$DB_HOST" -u"$DB_USER" "$DB_NAME" -N -e "$1" 2>/dev/null; }
# 【D3/D4 2026-10-21】旧写法把 DB_PASS/DB_USER 拼进 ssh 命令行 argv(本机 ps 可见口令)；
# 现改为整段远端脚本 + SQL + 口令全部经 stdin(bash -s)传递，远端口令只进 docker exec 的 env。
# stdin 型调用必须用 *_once 封装(重试型封装第二次尝试会读到 EOF)。
# shellcheck disable=SC2329
mysql_remote() {
  {
    printf 'DB_PASS=%q\n' "$DB_PASS"
    printf 'DB_USER=%q\n' "$DB_USER"
    printf 'DB_NAME=%q\n' "$DB_NAME"
    printf 'CONTAINER=%q\n' "$CONTAINER"
    cat <<'MREMOTE'
docker exec -i -e MYSQL_PWD="$DB_PASS" "$CONTAINER" mysql -u"$DB_USER" "$DB_NAME" -N --default-character-set=utf8mb4 2>/dev/null <<'SQL'
MREMOTE
    printf '%s\n' "$1"
    echo 'SQL'
  } | "$SSH_FN" "bash -s"
}

if [ "$MODE" = "auto" ]; then
  if command -v mysql >/dev/null 2>&1; then MODE=local; else MODE=remote; fi
fi

if [ "$MODE" = "remote" ]; then
  # shellcheck source=deploy-lib.sh
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/deploy-lib.sh"
  # 【D4】stdin 型调用必须用 *_once 封装——重试型 ssh_prod/ssh_test 第二次尝试会读到 EOF
  if [ "$DB_HOST" = "${PROD_HOST:-}" ]; then SSH_FN=ssh_prod_once
  elif [ "$DB_HOST" = "${TEST_HOST:-}" ]; then SSH_FN=ssh_test_once
  elif [ -n "${DB_SSH_HOST:-}" ]; then
    SSH_FN=ssh_custom
    # shellcheck disable=SC2329
    ssh_custom() {
      # 【D4 2026-10-21】默认/兜底 opts 均为主机密钥校验开启(=yes + BatchMode + 固定 known_hosts，
      # 复用 deploy-lib 的 AIGICRM_SSH_OPTS_STR)；口令经 SSHPASS env + sshpass -e，不进 argv。
      # 注意：不能把 =yes 追加在用户 SSH_OPTS 之后覆盖 =no——OpenSSH 同参数取第一个值，
      # deploy-lib 已在源头剔除用户校验类参数，这里直接使用其结果。
      local -a ssh_opts=()
      if [ -n "${AIGICRM_SSH_OPTS_STR:-}" ]; then
        read -r -a ssh_opts <<< "$AIGICRM_SSH_OPTS_STR"
      else
        read -r -a ssh_opts <<< "-o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$HOME/.ssh/known_hosts"
      fi
      SSHPASS="${DB_SSH_PASS:-}" sshpass -e ssh "${ssh_opts[@]}" "${DB_SSH_USER:-root}@$DB_SSH_HOST" "$@"
    }
  else
    echo "错误: remote 模式无法确定 SSH 目标" >&2; exit 1
  fi
  RUN_CMD=mysql_remote
else
  command -v mysql >/dev/null 2>&1 || { echo "错误: local 模式但本机无 mysql 客户端" >&2; exit 1; }
  RUN_CMD=mysql_direct
fi

# ---- 断言 ----
FAILS=0
assert_eq() { # $1=标签 $2=期望 $3=实际
  if [ "${3:-}" = "$2" ]; then
    echo "  ✅ $1: $3"
  else
    echo "  ❌ $1: 期望 '$2', 实际 '${3:-EMPTY/失败}'"
    FAILS=$((FAILS+1))
  fi
}

echo "== selectEffectiveGroupId: NULL 继承 =="
# 合成: 1(g1)→2(NULL)→3(NULL), 2→4(g9). selectEffectiveGroupId(3) 应解析祖先集团 1
eff3=$("$RUN_CMD" "WITH RECURSIVE seed AS (SELECT 1 id,0 parent_id,1 group_id,1 status UNION ALL SELECT 2,1,NULL,1 UNION ALL SELECT 3,2,NULL,1 UNION ALL SELECT 4,2,9,1), anc AS (SELECT id,parent_id,group_id,0 AS depth FROM seed WHERE id=3 UNION ALL SELECT d.id,d.parent_id,d.group_id,a.depth+1 FROM seed d INNER JOIN anc a ON d.id=a.parent_id) SELECT COALESCE(group_id,'NULL') FROM (SELECT group_id FROM anc WHERE group_id IS NOT NULL ORDER BY depth ASC LIMIT 1) t;")
assert_eq "Q1 有效集团 NULL 继承(3→祖先1)" "1" "$eff3"

echo "== selectDescendantGroupIds: 子树跨集团检测 =="
# 合成: 同树, selectDescendantGroupIds(2) 应返回 [9](子树含跨集团节点, 生产逻辑会拒绝)
sub2=$("$RUN_CMD" "WITH RECURSIVE seed AS (SELECT 1 id,0 parent_id,1 group_id,1 status UNION ALL SELECT 2,1,NULL,1 UNION ALL SELECT 3,2,NULL,1 UNION ALL SELECT 4,2,9,1), dept_tree AS (SELECT id,parent_id,group_id FROM seed WHERE id=2 AND status=1 UNION ALL SELECT d.id,d.parent_id,d.group_id FROM seed d INNER JOIN dept_tree t ON d.parent_id=t.id AND d.status=1) SELECT COALESCE(GROUP_CONCAT(group_id),'EMPTY') FROM (SELECT DISTINCT group_id FROM dept_tree WHERE id<>2 AND group_id IS NOT NULL) t;")
assert_eq "Q2 子树跨集团(2 含节点9)" "9" "$sub2"
# selectDescendantGroupIds(3) 应为 EMPTY(无子节点)
sub3=$("$RUN_CMD" "WITH RECURSIVE seed AS (SELECT 1 id,0 parent_id,1 group_id,1 status UNION ALL SELECT 2,1,NULL,1 UNION ALL SELECT 3,2,NULL,1 UNION ALL SELECT 4,2,9,1), dept_tree AS (SELECT id,parent_id,group_id FROM seed WHERE id=3 AND status=1 UNION ALL SELECT d.id,d.parent_id,d.group_id FROM seed d INNER JOIN dept_tree t ON d.parent_id=t.id AND d.status=1) SELECT COALESCE(GROUP_CONCAT(group_id),'EMPTY') FROM (SELECT DISTINCT group_id FROM dept_tree WHERE id<>3 AND group_id IS NOT NULL) t;")
assert_eq "Q3 子树无子节点(3)" "EMPTY" "$sub3"

echo "== 真实库语法/行为抽查(有数据时) =="
any_dept=$("$RUN_CMD" "SELECT id FROM sys_dept WHERE status=1 ORDER BY id LIMIT 1;" 2>/dev/null)
if [ -n "$any_dept" ]; then
  realeff=$("$RUN_CMD" "WITH RECURSIVE anc AS (SELECT id,parent_id,group_id,0 AS depth FROM sys_dept WHERE id=$any_dept UNION ALL SELECT d.id,d.parent_id,d.group_id,a.depth+1 FROM sys_dept d INNER JOIN anc a ON d.id=a.parent_id) SELECT COALESCE(group_id,'NULL') FROM (SELECT group_id FROM anc WHERE group_id IS NOT NULL ORDER BY depth ASC LIMIT 1) t;")
  echo "  ℹ️  Q4 真实库 dept#$any_dept 有效集团 = ${realeff:-查询失败}"
  realsub=$("$RUN_CMD" "WITH RECURSIVE dept_tree AS (SELECT id,parent_id,group_id FROM sys_dept WHERE id=$any_dept AND status=1 UNION ALL SELECT d.id,d.parent_id,d.group_id FROM sys_dept d INNER JOIN dept_tree t ON d.parent_id=t.id AND d.status=1) SELECT COALESCE(GROUP_CONCAT(DISTINCT group_id),'EMPTY') FROM dept_tree WHERE id<>$any_dept AND group_id IS NOT NULL;")
  echo "  ℹ️  Q5 真实库 dept#$any_dept 子树显式集团 = ${realsub:-查询失败}"
else
  echo "  ⚠️  sys_dept 无数据, 跳过真实库抽查"
fi

echo ""
if [ "$FAILS" -eq 0 ]; then
  echo "✅ SQL 验证全部通过"
  exit 0
else
  echo "❌ SQL 验证失败 $FAILS 项"
  exit 1
fi
