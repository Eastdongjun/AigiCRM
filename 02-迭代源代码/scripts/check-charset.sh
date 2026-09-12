#!/bin/bash
# ============================================================
# 中文乱码门禁（2026-08-14 建立，回应第二次 mojibake 事故）
# 用途：SQL 迁移/部署后扫描关键中文字段，断言无 mojibake 特征。
# 规则：凡向 MySQL 写入含中文的 SQL，执行后必须过本门禁。
# 用法：check-charset.sh [test|prod]
#   依赖 ~/.aigicrm-deploy.env（PROD_*/TEST_*、DB_PASSWORD/TEST_DB_ROOT_PASSWORD）
# 退出码：0=通过；1=发现乱码或连接失败
# ============================================================
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${HOME}/.aigicrm-deploy.env"
[ -f "$ENV_FILE" ] || { echo "❌ 缺少 $ENV_FILE"; exit 1; }
# shellcheck disable=SC1090
source "$ENV_FILE"

TARGET="${1:-test}"
# mojibake 特征：UTF-8 中文被 latin1 解释后的典型字节序列（æ/Ã/â€/å 等）
MOJI_PATTERN='[æÃâ€åØ]'

run_check() {
  local label="$1" host="$2" user="$3" pass="$4" ct="$5" dbpass="$6"
  echo "=== [$label] 中文字段扫描 ==="
  local dbq out
  dbq=$(printf %q "$dbpass")  # 【2026-08-14 评审 P2】%q 转义后再嵌入远端命令，口令不进命令行 -p 参数
  # 库名不得硬编码（原为 aigicrm）：生产实际是 ycmn，硬编码会连错库。
  local scan_db="${SCAN_DB:-${DB_NAME:-aigicrm}}"
  out=$(sshpass -p "$pass" ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$user@$host" \
    "docker exec -e MYSQL_PWD=$dbq $ct mysql -h127.0.0.1 -uroot --default-character-set=utf8mb4 $scan_db -N -e 'SELECT CONCAT(role_code, \"=\", role_name) FROM sys_role WHERE role_code LIKE \"incentive%\"; SELECT CONCAT(permission, \"=\", menu_name) FROM sys_menu WHERE permission IN (\"incentive:manage\",\"incentive:view-all\",\"incentive:view-shared\",\"file:acl:manage\");' 2>/dev/null" 2>/dev/null)
  if [ -z "$out" ]; then
    echo "❌ [$label] 查询失败或返回空（连接/库名错误？）"
    return 1
  fi
  if echo "$out" | grep -qE "$MOJI_PATTERN"; then
    echo "❌ [$label] 发现乱码："
    echo "$out" | grep -E "$MOJI_PATTERN"
    echo "   修复：执行 sql/migration/20260814/m1-fix-mojibake.sql（连接带 --default-character-set=utf8mb4）"
    return 1
  fi
  echo "✅ [$label] 中文字段无乱码"
  echo "$out" | sed 's/^/   /'
  return 0
}

case "$TARGET" in
  test)
    # 测试服 app 的 DB_HOST=shared-mysql（容器名 shared-mysql；aigicrm-mysql 是另一个容器，勿混淆）
    run_check "测试服" "$TEST_HOST" "$TEST_USER" "$TEST_PASS" "shared-mysql" "${TEST_DB_ROOT_PASSWORD}" || exit 1
    ;;
  prod)
    # 生产 app 的 DB_HOST=mysql（网络别名），容器名 aigicrm-mysql
    run_check "生产" "$PROD_HOST" "$PROD_USER" "$PROD_PASS" "${APP_SLUG:-aigicrm}-mysql" "$DB_PASSWORD" || exit 1
    ;;
  *)
    echo "用法: $0 [test|prod]"; exit 2
    ;;
esac
exit 0
