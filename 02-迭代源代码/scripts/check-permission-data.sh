#!/bin/bash
# scripts/check-permission-data.sh — 权限数据前提体检(设计文档 §12.2, 统一权限体系)
# 检查 sys_dept.dept_type / sys_dept.group_id / sys_user.manager_id / 核心业务表 tenant_id 完整性
# manager_id 为渐进维护项(设计 §12.2): 非零仅告警, 不计失败; 其余检查非零需人工介入计失败
# tenant_id 由 Task 12 迁移后才有列: 列不存在时输出"未迁移", 不视为失败
# 用法:
#   check-permission-data.sh <DB_HOST> <DB_USER> <DB_PASS> <DB_NAME> [选项]
#   省略参数时从环境变量读取: DB_HOST/DB_USER/DB_PASS/DB_NAME
# 选项:
#   --remote            经 SSH + docker exec 在服务器容器内执行(默认容器 ycmn-mysql, 同 deploy-db.sh)
#   --local             本机 mysql 客户端直连(需本机安装 mysql client)
#   --container NAME    指定远端容器名(仅 --remote)
#   --export-missing F  导出 deptType/group_id 缺失部门明细到文件(供数据清洗)
#   缺省自动判定模式: 本机有 mysql 客户端 → local; 否则 → remote
# remote 模式 SSH 目标: DB_HOST 匹配 ~/.ycmn-deploy.env 的 PROD_HOST/TEST_HOST 时自动复用
#   ssh_prod/ssh_test 凭据; 否则需 DB_SSH_HOST/DB_SSH_USER/DB_SSH_PASS 环境变量
# 退出码: 0=检查通过(manager_id 渐进项告警不计失败); 1=存在非零缺失项(需人工介入)或执行失败
set -uo pipefail

usage() {
  # 仅输出文件头注释(至第一个非注释行)
  awk 'NR > 1 { if ($0 ~ /^#/) { sub(/^# ?/, "", $0); print } else exit }' "$0"
}

# ---- 参数解析(位置参数 = host/user/pass/db, 与 --remote 等选项可混排) ----
DB_HOST="${DB_HOST:-}"; DB_USER="${DB_USER:-}"; DB_PASS="${DB_PASS:-}"; DB_NAME="${DB_NAME:-}"
MODE=auto; CONTAINER=ycmn-mysql; EXPORT_FILE=""; POSN=0
while [ $# -gt 0 ]; do
  case "$1" in
    --remote) MODE=remote; shift ;;
    --local) MODE=local; shift ;;
    --container) CONTAINER="${2:-}"; shift 2 ;;
    --export-missing) EXPORT_FILE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*)
      echo "未知参数: $1" >&2; usage; exit 2 ;;
    *)
      POSN=$((POSN+1))
      case "$POSN" in
        1) DB_HOST=$1 ;; 2) DB_USER=$1 ;; 3) DB_PASS=$1 ;; 4) DB_NAME=$1 ;;
        *) echo "错误: 位置参数最多 4 个(host/user/pass/db)" >&2; exit 2 ;;
      esac
      shift ;;
  esac
done
# C6: 未显式提供时从 ~/.ycmn-deploy.env 派生默认（生产 root + 已轮换 DB_PASSWORD，复用 ssh_prod 凭据）
if [ -z "$DB_HOST" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASS" ] || [ -z "$DB_NAME" ]; then
  ENV_FILE="$HOME/.ycmn-deploy.env"
  [ -f "$ENV_FILE" ] && source "$ENV_FILE"
  DB_HOST="${DB_HOST:-$PROD_HOST}"
  DB_USER="${DB_USER:-root}"
  DB_PASS="${DB_PASS:-$DB_PASSWORD}"
  DB_NAME="${DB_NAME:-ycmn}"
fi
if [ -z "$DB_HOST" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASS" ] || [ -z "$DB_NAME" ]; then
  echo "错误: 缺少 DB 连接参数(host/user/pass/db)" >&2; usage; exit 2
fi
[ -n "$CONTAINER" ] || { echo "错误: --container 需要容器名" >&2; exit 2; }

# ---- 执行模式 ----
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# local: 本机 mysql 客户端直连
mysql_direct() { mysql -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "$1" 2>/dev/null; }
# remote: 经 ssh + docker exec -i 从 stdin 喂 SQL, 免去远程引号转义;
# 无 -h 走容器默认 socket(同 deploy-db.sh 的连接模式, 不依赖 TCP 监听)
mysql_remote() { printf '%s' "$1" | "$SSH_FN" "docker exec -i $CONTAINER mysql -u$DB_USER -p$DB_PASS $DB_NAME -N --default-character-set=utf8mb4 2>/dev/null"; }

if [ "$MODE" = "auto" ]; then
  if command -v mysql >/dev/null 2>&1; then MODE=local; else MODE=remote; fi
fi

if [ "$MODE" = "remote" ]; then
  source "$SCRIPT_DIR/deploy-lib.sh"
  if [ "$DB_HOST" = "${PROD_HOST:-}" ]; then SSH_FN=ssh_prod
  elif [ "$DB_HOST" = "${TEST_HOST:-}" ]; then SSH_FN=ssh_test
  elif [ -n "${DB_SSH_HOST:-}" ]; then
    SSH_FN=ssh_custom
    ssh_custom() { sshpass -p "${DB_SSH_PASS:-}" ssh ${SSH_OPTS:--o ConnectTimeout=20 -o StrictHostKeyChecking=no} "${DB_SSH_USER:-root}@$DB_SSH_HOST" "$@"; }
  else
    echo "错误: remote 模式无法确定 SSH 目标(DB_HOST 未匹配 PROD_HOST/TEST_HOST 且未设置 DB_SSH_HOST)" >&2
    exit 1
  fi
  RUN_CMD=mysql_remote
  MODE_TAG="remote($SSH_FN/$CONTAINER)"
else
  command -v mysql >/dev/null 2>&1 || { echo "错误: local 模式但本机无 mysql 客户端" >&2; exit 1; }
  RUN_CMD=mysql_direct
  MODE_TAG="local"
fi

# ---- 体检 ----
BAD=0

# 通用计数检查: 非零 → 人工介入
report_count() { # $1=标签 $2=SQL
  local cnt
  cnt=$("$RUN_CMD" "$2") || { echo "  ❌ $1: 查询失败"; BAD=$((BAD+1)); return; }
  if [ "$cnt" -eq 0 ]; then
    echo "  ✅ $1: 0"
  else
    echo "  ❌ $1: $cnt —— 人工介入(数据清洗)"
    BAD=$((BAD+1))
  fi
}

# 渐进维护项(设计 §12.2): 非零仅告警, 不计失败(老数据大量为空, 计失败会持续拦交付)
report_warn() { # $1=标签 $2=SQL
  local cnt
  cnt=$("$RUN_CMD" "$2") || { echo "  ❌ $1: 查询失败"; BAD=$((BAD+1)); return; }
  if [ "$cnt" -eq 0 ]; then
    echo "  ✅ $1: 0"
  else
    echo "  ⚠️ $1: $cnt —— 渐进维护项, 不计失败"
  fi
}

# tenant_id 检查: 列不存在(未迁移)不视为失败
check_tenant() { # $1=表名
  local t="$1" cols cnt
  cols=$("$RUN_CMD" "SHOW COLUMNS FROM $t LIKE 'tenant_id';") || { echo "  ❌ $t: tenant_id 检查失败(表不存在或连接错误)"; BAD=$((BAD+1)); return; }
  if [ -z "$cols" ]; then
    echo "  — $t: tenant_id 列未迁移(Task 12 迁移后复检)"
  else
    cnt=$("$RUN_CMD" "SELECT COUNT(*) FROM $t WHERE tenant_id IS NULL OR tenant_id=0;") || { echo "  ❌ $t: tenant_id 计数查询失败"; BAD=$((BAD+1)); return; }
    if [ "$cnt" -eq 0 ]; then
      echo "  ✅ $t: tenant_id 空值 0"
    else
      echo "  ❌ $t: tenant_id 空值 $cnt —— 人工介入"
      BAD=$((BAD+1))
    fi
  fi
}

echo "== 权限数据前提体检: $DB_HOST / $DB_NAME ($MODE_TAG) =="
report_count "deptType 为空的组织数" "SELECT COUNT(*) FROM sys_dept WHERE (dept_type IS NULL OR dept_type=0);"
report_count "group_id 为空的业务组织数" "SELECT COUNT(*) FROM sys_dept WHERE group_id IS NULL;"
# 【2026-08-12 P2(c) 子树集团一致性】跨集团子树: 子树内显式 group_id 与最近祖先携带集团冲突的节点数。
# 写侧 validateRoleOrgsGroup 已拒绝新建此类子树, 此处为存量数据体检(carry CTE 沿树携带 eff, 冲突即计数)
report_count "跨集团子树冲突组织数" "WITH RECURSIVE carry AS (SELECT id,parent_id,group_id,group_id AS eff,CAST(NULL AS SIGNED) AS parent_eff FROM sys_dept WHERE parent_id=0 AND status=1 UNION ALL SELECT d.id,d.parent_id,d.group_id,COALESCE(d.group_id,c.eff) AS eff,c.eff AS parent_eff FROM sys_dept d INNER JOIN carry c ON d.parent_id=c.id AND d.status=1) SELECT COUNT(*) FROM carry WHERE group_id IS NOT NULL AND group_id != parent_eff;"
report_warn "manager_id 为空的启用用户数" "SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND status=1 AND manager_id IS NULL;"
echo "-- 白名单表 tenant_id 覆盖(未迁移时输出未迁移) --"
# 白名单 8 表与 MybatisPlusConfig.TENANT_TABLES 对齐（含已迁移的 share/file/sea_pool/approval/reminder）
TENANT_TABLES="biz_customer biz_customer_share biz_file_folder biz_file biz_incentive_record biz_sea_pool biz_approval biz_reminder"
for t in $TENANT_TABLES; do check_tenant "$t"; done

# 缺失明细导出(供数据清洗, Task 10 审查衔接项)
if [ -n "$EXPORT_FILE" ]; then
  if : > "$EXPORT_FILE" 2>/dev/null; then
    {
      echo "# deptType 缺失部门 (id, dept_name)"
      "$RUN_CMD" "SELECT id, dept_name FROM sys_dept WHERE (dept_type IS NULL OR dept_type=0);" || true
      echo ""
      echo "# group_id 缺失部门 (id, dept_name)"
      "$RUN_CMD" "SELECT id, dept_name FROM sys_dept WHERE group_id IS NULL;" || true
    } >> "$EXPORT_FILE"
    echo "已导出缺失明细: $EXPORT_FILE"
  else
    echo "  ❌ 无法写入导出文件: $EXPORT_FILE"
    BAD=$((BAD+1))
  fi
fi

echo "== 完成: 非零缺失项 $BAD =="
if [ "$BAD" -eq 0 ]; then
  echo "✅ 权限数据前提体检通过(全部为零)"
  exit 0
else
  echo "❌ 权限数据前提体检存在非零缺失项(需人工介入)或查询失败"
  exit 1
fi
