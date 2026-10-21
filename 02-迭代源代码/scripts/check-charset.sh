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

TARGET="${1:-test}"
case "$TARGET" in
  test) export AIGICRM_TEST_ONLY=1 ;; # 测试目标不需要生产 DB_PASSWORD（deploy-lib 的显式逃生）
  prod) : ;;
  *) echo "用法: $0 [test|prod]"; exit 2 ;;
esac

# 【D4 2026-10-21】SSH 复用 deploy-lib 加固通道：StrictHostKeyChecking=yes + BatchMode + 固定
# known_hosts，生产仅公钥(PROD_KEY)。旧写法 sshpass -p 且关闭主机密钥校验，使
# 口令进本机 argv(ps 可见)且备份/巡检主机可被中间人冒充。
# shellcheck source=deploy-lib.sh
# shellcheck disable=SC1091
source "$SCRIPT_DIR/deploy-lib.sh"

MOJI_PATTERN='[æÃâ€åØ]'

# 【D3 2026-10-21】DB 口令与 SQL 全部经 stdin(bash -s)传递，不再拼进 ssh 命令行 argv；
# 远端口令只作为 docker exec 的 MYSQL_PWD env（与 deploy-db.sh 同口径）。
# stdin 型调用必须用 *_once 封装（重试型封装第二次尝试会读到 EOF）。
run_check() { # $1=标签 $2=ssh_fn $3=容器 $4=DB口令
  local label="$1" sshfn="$2" ct="$3" dbpass="$4"
  echo "=== [$label] 中文字段扫描 ==="
  if [ -z "$dbpass" ]; then
    echo "❌ [$label] 缺少 DB 口令(env: TEST_DB_ROOT_PASSWORD / DB_PASSWORD)"
    return 1
  fi
  # 库名不得硬编码（原为 aigicrm）：生产实际是 ycmn，硬编码会连错库。
  local scan_db="${SCAN_DB:-${DB_NAME:-aigicrm}}" out
  out="$({
    printf 'DBQ=%q\n' "$dbpass"
    printf 'CT=%q\n' "$ct"
    printf 'SCAN_DB=%q\n' "$scan_db"
    cat <<'REMOTE'
docker exec -i -e MYSQL_PWD="$DBQ" "$CT" mysql -h127.0.0.1 -uroot --default-character-set=utf8mb4 "$SCAN_DB" -N 2>/dev/null <<'SQL'
SELECT CONCAT(role_code, "=", role_name) FROM sys_role WHERE role_code LIKE "incentive%";
SELECT CONCAT(permission, "=", menu_name) FROM sys_menu WHERE permission IN ("incentive:manage","incentive:view-all","incentive:view-shared","file:acl:manage");
SQL
REMOTE
  } | "$sshfn" "bash -s")"
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
    run_check "测试服" ssh_test_once shared-mysql "${TEST_DB_ROOT_PASSWORD:-}" || exit 1
    ;;
  prod)
    # 生产 app 的 DB_HOST=mysql（网络别名），容器名 aigicrm-mysql
    run_check "生产" ssh_prod_once "${APP_SLUG:-aigicrm}-mysql" "${DB_PASSWORD:-}" || exit 1
    ;;
esac
exit 0
