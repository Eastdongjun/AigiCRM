#!/usr/bin/env bash
# ============================================================
# 迁移后断言（只读）—— GL-07 2026-11-24
#
# 为什么需要: deploy-db.sh 此前迁移完只打印表数/菜单数/客户数, 不做任何判断。本批迁移里有 2 支
#   (改名、移动菜单) 按自然键定位, 目标行不存在时"0 行受影响"也照样成功 —— 不核对就发现不了。
#
# 核对内容(全部是 SELECT, 不写任何数据):
#   ① Flyway 历史: 没有失败行; 晚于 --since 的每支迁移都已成功应用; 最新一条等于迁移目录里最新的文件
#   ② 内容类型重构: 没有旧值残留; 与迁移前计数对账(每日诵读=原诵读数, 每日随施=原心得+正能量数, 总量不变)
#   ③ 复诵表有 tenant_id 列; 全库含 tenant_id 的表只有 1 这一个取值
#   ④ 菜单: 统计分析组存在且唯一、统计看板已移到它下面、三个菜单名、法布施组内排序
#   ⑤ 权限元数据与动作名已改名
#   ⑥ 行业词表的值都在(从迁移文件解析, 不另抄一份)
#   ⑦ 存量安装已写"首启向导已完成"标记
#   ⑧ 提醒频率没有旧值残留
#
# 用法:
#   bash scripts/verify-post-migrate.sh <prod|test> [--since <版本>] [--pre <诵读,心得,正能量,总量>]
#   bash scripts/verify-post-migrate.sh <prod|test> --snapshot
#     --since     只核对晚于该版本的迁移。不给则默认 2026.11.22.1(生产当前版本)
#     --pre       迁移前 biz_fabushi_post 按旧类型的计数(诵读,心得,正能量,总量)
#     --snapshot  只做迁移前快照并退出: 输出 SINCE=<当前已应用版本> 与 PRE=<旧类型计数> 两行,
#                 迁移脚本在迁移前调用, 迁移后把这两个值传回 --since / --pre
# 退出码: 0=全部通过; 1=至少一条断言失败(含查询本身失败); 2=无法完成验证(缺迁移前计数、参数非法等)
#
# 可测试性: 设置 AIGICRM_SQL_RUNNER="<命令>" 后, 每条 SQL 经 stdin 交给该命令执行, 不再连接任何服务器
#   (行为测试见 scripts/test-verify-post-migrate.sh)。每条 SQL 首行带注释标签 -- vpm:<tag>。
# ============================================================
set -uo pipefail

SCRIPT_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usage() { sed -n '2,29p' "$0" | sed 's/^# \{0,1\}//'; }

case "${1:-}" in -h|--help) usage; exit 0 ;; esac
TARGET="${1:-}"
[ $# -gt 0 ] && shift
SINCE="2026.11.22.1"
PRE=""
SNAPSHOT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --since)    SINCE="${2:-}"; shift 2 ;;
    --pre)      PRE="${2:-}";   shift 2 ;;
    --snapshot) SNAPSHOT=1;     shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; echo "用法: bash scripts/verify-post-migrate.sh <prod|test> [--since <版本>] [--pre <诵读,心得,正能量,总量>]" >&2; exit 2 ;;
  esac
done

case "$SINCE" in
  *[!0-9.]*|'') echo "❌ --since 版本号格式不对: '${SINCE}'(应为点分数字, 如 2026.11.22.1)" >&2; exit 2 ;;
esac
PRE_RECI="" PRE_HEART="" PRE_ZNL="" PRE_TOTAL=""
if [ -n "$PRE" ]; then
  IFS=, read -r PRE_RECI PRE_HEART PRE_ZNL PRE_TOTAL _ <<<"$PRE"
  for v in "$PRE_RECI" "$PRE_HEART" "$PRE_ZNL" "$PRE_TOTAL"; do
    case "$v" in
      ''|*[!0-9]*) echo "❌ --pre 必须是四个非负整数(诵读,心得,正能量,总量), 实际: '${PRE}'" >&2; exit 2 ;;
    esac
  done
fi

case "$TARGET" in
  prod)
    # shellcheck disable=SC1091
    source "$SCRIPT_SELF_DIR/deploy-lib.sh"
    CT="${APP_SLUG}-mysql"; DB="$DB_NAME"; SSH_ONCE=ssh_prod_once ;;
  test)
    # 测试库通道: 不要求生产口令(与 deploy-db-test.sh 同一逃生门)
    export AIGICRM_TEST_ONLY=1
    # shellcheck disable=SC1091
    source "$SCRIPT_SELF_DIR/deploy-lib.sh"
    CT="${TEST_DB_CONTAINER:-shared-mysql}"; DB="${TEST_DB_NAME:-aigicrm_prod_20261016}"; SSH_ONCE=ssh_test_once ;;
  *)
    echo "用法: bash scripts/verify-post-migrate.sh <prod|test> [--since <版本>] [--pre <诵读,心得,正能量,总量>]" >&2
    exit 2 ;;
esac

MIG_DIR="${AIGICRM_MIGRATION_DIR:-$SCRIPT_SELF_DIR/../aigicrm-backend/src/main/resources/db/migration}"
[ -d "$MIG_DIR" ] || { echo "❌ 迁移目录不存在: $MIG_DIR" >&2; exit 2; }

# ---------- 输出与结果计数 ----------
PASS_N=0; FAIL_N=0; BLOCK_N=0
ok()      { echo "  PASS     $1"; PASS_N=$((PASS_N + 1)); }
bad()     { echo "  FAIL     $1" >&2; FAIL_N=$((FAIL_N + 1)); }
blocked() { echo "  BLOCKED  $1" >&2; BLOCK_N=$((BLOCK_N + 1)); }
# 提示语里变量后紧跟全角标点时一律写成 ${var}: macOS bash 3.2 不把全角字符当作变量名边界。
num() { # 把单个查询值规范成非负整数; 不是整数则输出空串
  local v
  v="$(printf '%s' "$1" | tr -d '[:space:]')"
  case "$v" in ''|*[!0-9]*) printf '' ;; *) printf '%s' "$v" ;; esac
}

# ---------- 执行 SQL ----------
# 缺省经 ssh 在 MySQL 容器内执行; 设置 AIGICRM_SQL_RUNNER 后交给该命令(仅用于行为测试)。
# 远端脚本先落本地临时文件再执行, 不用"命令替换里嵌 heredoc"的写法(见 rollback.sh 头部的 bash 3.2 说明)。
sql_remote() {
  local sql="$1" tmp out rc
  tmp="$(mktemp "${TMPDIR:-/tmp}/aigicrm-vpm.XXXXXX")" || return 1
  chmod 600 "$tmp"
  {
    echo 'set -Eeuo pipefail'
    printf 'CT=%q\nDB=%q\n' "$CT" "$DB"
    cat <<'REMOTE_HEAD'
PW="$(docker exec "$CT" sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
[ -n "$PW" ] || { echo "无法从容器 $CT 读取数据库 root 口令" >&2; exit 1; }
exec docker exec -i -e MYSQL_PWD="$PW" "$CT" mysql -uroot -N -B --default-character-set=utf8mb4 "$DB" <<'VPM_SQL'
REMOTE_HEAD
    printf '%s\n' "$sql"
    echo 'VPM_SQL'
  } > "$tmp" || { rm -f "$tmp"; return 1; }
  out="$("$SSH_ONCE" "bash -s" < "$tmp")"; rc=$?
  rm -f "$tmp"
  [ "$rc" -eq 0 ] || { printf '%s\n' "$out" >&2; return 1; }
  printf '%s\n' "$out"
}
sql() {
  if [ -n "${AIGICRM_SQL_RUNNER:-}" ]; then
    # 有意不加引号: 允许 AIGICRM_SQL_RUNNER 里带参数
    printf '%s\n' "$1" | $AIGICRM_SQL_RUNNER
    return $?
  fi
  sql_remote "$1"
}
query() { # $1=标签 $2=SQL 正文(不含标签); 成功把结果放进全局 Q, 失败计一条 FAIL 并返回 1
  Q="$(sql "-- vpm:$1
$2")" || { bad "query_failed $1(查询本身失败, 无法核对)"; return 1; }
}

# ---------- 迁移前快照(迁移脚本在迁移前调用) ----------
# 输出 SINCE=<当前已应用的最新版本> 与 PRE=<诵读,心得,正能量,总量>; 迁移后原样传回 --since / --pre。
if [ "$SNAPSHOT" -eq 1 ]; then
  if ! query snap_version "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"; then
    echo "❌ 读不到 Flyway 历史, 无法确定迁移前版本 —— 中止(否则迁移后对不出本批范围)" >&2; exit 1
  fi
  snap_ver="$(printf '%s\n' "$Q" | tail -1 | tr -d '\r[:space:]')"
  case "$snap_ver" in
    '') snap_ver="0" ;;      # 全新库: 还没有任何已应用的版本, 本批 = 全部迁移
    *[!0-9.]*) echo "❌ Flyway 历史里的最新版本号不是点分数字: [${snap_ver}]" >&2; exit 1 ;;
  esac
  # 全新库上这条查询会失败(表还不存在)。按 0 记是安全的: 迁移后断言会重新计数对账, 对不上就不通过。
  if Q="$(sql "-- vpm:snap_counts
SELECT COALESCE(SUM(scope_type = 'recitation'), 0), COALESCE(SUM(scope_type = 'heart'), 0), COALESCE(SUM(scope_type = 'znl'), 0), COUNT(*) FROM biz_fabushi_post")"; then
    snap_pre="$(printf '%s\n' "$Q" | tail -1 | tr -d '\r' | tr '\t' ',')"
    IFS=, read -r a b c d extra <<< "$snap_pre"
    for n in "$a" "$b" "$c" "$d"; do
      case "$n" in ''|*[!0-9]*) echo "❌ 迁移前的内容类型计数无法解析: [${snap_pre}]" >&2; exit 1 ;; esac
    done
    [ -z "${extra:-}" ] || { echo "❌ 迁移前的内容类型计数应为 4 个数, 实际: [${snap_pre}]" >&2; exit 1; }
  else
    snap_pre="0,0,0,0"
    echo "  注意: 读不到 biz_fabushi_post 的计数(全新库表还不存在), 迁移前内容类型按 0 记" >&2
  fi
  printf 'SINCE=%s\nPRE=%s\n' "$snap_ver" "$snap_pre"
  exit 0
fi

echo "════════ 迁移后断言(目标=${TARGET} 库=${DB})只读, 不写任何数据 ════════"

# ---------- ① Flyway 历史 ----------
# "本批"= 迁移目录里晚于 SINCE 的全部文件, 不写死数量; 以后新增迁移不用改这里。
all_versions() { ls "$MIG_DIR" | sed -n 's/^V\([0-9][0-9.]*\)__.*\.sql$/\1/p' | sort -t. -k1,1n -k2,2n -k3,3n -k4,4n; }
newer() { [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$2" "$1" | sort -t. -k1,1n -k2,2n -k3,3n -k4,4n | head -1)" = "$2" ]; }
newest="$(all_versions | tail -1)"
batch=""
for v in $(all_versions); do newer "$v" "$SINCE" && batch="${batch:+${batch} }${v}"; done
batch_n=0; for v in ${batch:-}; do batch_n=$((batch_n + 1)); done
[ "$batch_n" -gt 0 ] || { echo "❌ 迁移目录里没有晚于 ${SINCE} 的迁移, 没有可核对的内容" >&2; exit 2; }
echo "本批迁移(晚于迁移前版本)共 ${batch_n} 支: ${batch}"

if query flyway_failed "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0"; then
  n="$(num "$Q")"
  if [ -z "$n" ]; then bad "flyway_failed: 无法解析失败行数 [$(printf '%s' "$Q" | head -c 80)]"
  elif [ "$n" -eq 0 ]; then ok "flyway_failed: 没有失败的迁移记录"
  else bad "flyway_failed: 有 ${n} 条失败的迁移记录"; fi
fi

if query flyway_applied "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank"; then
  missing=""; last_applied=""
  for v in ${batch:-}; do
    printf '%s\n' "$Q" | grep -qx -- "$v" || missing="${missing:+${missing} }${v}"
  done
  last_applied="$(printf '%s\n' "$Q" | tail -1 | tr -d '\r[:space:]')"
  if [ -n "$missing" ]; then bad "flyway_applied: 本批迁移未成功应用: ${missing}"
  elif [ "$last_applied" != "$newest" ]; then bad "flyway_applied: 最新已应用版本是 ${last_applied}, 迁移目录最新文件是 ${newest}"
  else ok "flyway_applied: 本批 ${batch_n} 支全部成功应用, 最新为 ${last_applied}"; fi
fi

# ---------- ② 内容类型重构 ----------
# 对账口径来自 V2026.11.23.1: recitation→songdu, heart+znl→suishi, 总量不变。
post_songdu=""; post_suishi=""; post_zaidu=""; post_total=0; old_left=""
if query scope_dist "SELECT scope_type, COUNT(*) FROM biz_fabushi_post GROUP BY scope_type"; then
  while IFS=$'\t' read -r t c; do
    [ -n "$t" ] || continue
    n="$(num "$c")"
    if [ -z "$n" ]; then bad "scope_dist: 无法解析计数 [${t}]"; continue; fi
    post_total=$((post_total + n))
    case "$t" in
      songdu) post_songdu="$n" ;;
      suishi) post_suishi="$n" ;;
      zaidu)  post_zaidu="$n" ;;
      *)      old_left="${old_left} ${t}=${n}" ;;
    esac
  done <<< "$Q"
  if [ -n "$old_left" ]; then bad "scope_old_values: 仍有旧内容类型残留:${old_left}"
  else ok "scope_old_values: 没有旧内容类型残留"; fi
  if [ -z "$PRE" ]; then
    blocked "scope_reconcile: 未提供迁移前计数, 无法对账(--pre 诵读,心得,正能量,总量)"
  else
    want_suishi=$((PRE_HEART + PRE_ZNL))
    if [ "${post_songdu:-0}" -ne "$PRE_RECI" ]; then
      bad "scope_reconcile: 每日诵读 ${post_songdu:-0} 条, 迁移前诵读 ${PRE_RECI} 条"
    elif [ "${post_suishi:-0}" -ne "$want_suishi" ]; then
      bad "scope_reconcile: 每日随施 ${post_suishi:-0} 条, 迁移前心得+正能量 ${want_suishi} 条"
    elif [ "$post_total" -ne "$PRE_TOTAL" ]; then
      bad "scope_reconcile: 总量 ${post_total} 条, 迁移前 ${PRE_TOTAL} 条"
    else
      ok "scope_reconcile: 诵读 ${post_songdu:-0}=${PRE_RECI}, 随施 ${post_suishi:-0}=${PRE_HEART}+${PRE_ZNL}, 总量 ${post_total} 不变"
    fi
  fi
fi

# ---------- ③ 租户列与租户取值 ----------
if query recitation_tenant_column "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_fabushi_recitation' AND COLUMN_NAME = 'tenant_id'"; then
  n="$(num "$Q")"
  [ "$n" = "1" ] && ok "recitation_tenant_column: 复诵表已有 tenant_id 列" \
                 || bad "recitation_tenant_column: 复诵表缺 tenant_id 列(新后端的启动自检会因此拒绝启动)"
fi
if query tenant_tables "SELECT TABLE_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'tenant_id' ORDER BY TABLE_NAME"; then
  tables_ok=1; parts=()
  # 逐行读表名(不按空白拆词), 且要求整行只含字母数字下划线: 表名要拼进 SQL, 不能靠"以合法字符开头"放行
  while IFS= read -r t; do
    t="${t%$'\r'}"
    [ -n "$t" ] || continue
    case "$t" in
      *[!A-Za-z0-9_]*) bad "tenant_tables: 表名含非法字符, 拒绝拼进 SQL: [${t}]"; tables_ok=0; break ;;
      *) ;;
    esac
    parts+=("SELECT '${t}' AS tbl, COUNT(*) AS stray FROM \`${t}\` WHERE \`tenant_id\` IS NULL OR \`tenant_id\` <> 1 HAVING stray > 0")
  done <<< "$Q"
  if [ "$tables_ok" -eq 1 ]; then
    [ "${#parts[@]}" -gt 0 ] || { bad "tenant_tables: 没有任何含 tenant_id 的表(与预期不符)"; tables_ok=0; }
  fi
  if [ "$tables_ok" -eq 1 ]; then
    # 逐条拼接(不能靠 IFS 拼多字符分隔串: ${arr[*]} 只取 IFS 的第一个字符)
    union=""; sep=""
    for p in "${parts[@]}"; do union="${union}${sep}${p}"; sep=" UNION ALL "; done
    if query tenant_union "$union"; then
      [ -z "$(printf '%s' "$Q" | tr -d '[:space:]')" ] \
        && ok "tenant_single: ${#parts[@]} 张含 tenant_id 的表都只有 1 这一个取值" \
        || bad "tenant_single: 以下表的 tenant_id 出现了 1 以外的取值或空值: $(printf '%s' "$Q" | tr '\t' '=' | tr '\n' ' ')"
    fi
  fi
fi

# ---------- ④ 菜单 ----------
# 判定条件与三支迁移的 WHERE 保持一致(按路径和名称定位, 不写行 id)。
if query menu_stats_group "SELECT COUNT(*) FROM sys_menu WHERE menu_type = 1 AND menu_name = '统计分析' AND COALESCE(parent_id, 0) = 0"; then
  n="$(num "$Q")"
  [ "$n" = "1" ] && ok "menu_stats_group: 顶层「统计分析」组存在且唯一" \
                 || bad "menu_stats_group: 顶层「统计分析」组应有且只有 1 个, 实际 ${n:-?}(统计看板无处可移)"
fi
if query menu_stats_parent "SELECT COUNT(*) FROM sys_menu c JOIN sys_menu g ON g.id = c.parent_id AND g.menu_type = 1 AND g.menu_name = '统计分析' AND COALESCE(g.parent_id, 0) = 0 WHERE c.path = '/fabushi/stats' AND c.menu_type = 2"; then
  n="$(num "$Q")"
  [ "$n" = "1" ] && ok "menu_stats_parent: 统计看板已移到「统计分析」组下" \
                 || bad "menu_stats_parent: 统计看板应在「统计分析」组下, 实际命中 ${n:-?} 行"
fi
if query menu_names "SELECT path, menu_name FROM sys_menu WHERE menu_type = 2 AND path IN ('/fabushi/shelf', '/fabushi/feed', '/fabushi/content')"; then
  names_bad=""
  for pair in '/fabushi/shelf|我的觉醒' '/fabushi/feed|认知广场' '/fabushi/content|内容管理'; do
    p="${pair%%|*}"; want="${pair##*|}"
    got="$(printf '%s\n' "$Q" | awk -F'\t' -v p="$p" '$1 == p { print $2 }' | head -1)"
    [ "$got" = "$want" ] || names_bad="${names_bad} ${p}=[${got:-缺失}],应为「${want}」"
  done
  [ -z "$names_bad" ] && ok "menu_names: 我的觉醒 / 认知广场 / 内容管理 三个菜单名正确" \
                      || bad "menu_names:${names_bad}"
fi
if query menu_fabushi_order "SELECT m.path, m.sort_order FROM sys_menu m JOIN sys_menu g ON g.id = m.parent_id WHERE g.menu_type = 1 AND g.menu_name = '法布施' AND m.menu_type = 2 AND m.path IN ('/fabushi/shelf', '/fabushi/feed', '/fabushi/library', '/fabushi/book', '/fabushi/content')"; then
  order_bad=""
  for pair in '/fabushi/shelf|1' '/fabushi/feed|2' '/fabushi/library|3' '/fabushi/book|4' '/fabushi/content|5'; do
    p="${pair%%|*}"; want="${pair##*|}"
    got="$(printf '%s\n' "$Q" | awk -F'\t' -v p="$p" '$1 == p { print $2 }' | head -1 | tr -d '\r')"
    [ "$got" = "$want" ] || order_bad="${order_bad} ${p}=[${got:-缺失}],应为第 ${want}"
  done
  [ -z "$order_bad" ] && ok "menu_fabushi_order: 法布施组内顺序为 我的觉醒/认知广场/书目浏览/书目管理/内容管理" \
                      || bad "menu_fabushi_order:${order_bad}"
fi

# ---------- ⑤ 权限元数据与动作名 ----------
if query perm_meta_name "SELECT COUNT(*) FROM sys_permission_meta WHERE code = 'fabushi:content:view' AND name = '内容管理'"; then
  n="$(num "$Q")"
  [ "$n" = "1" ] && ok "perm_meta_name: 权限元数据已改名为「内容管理」" \
                 || bad "perm_meta_name: 权限元数据 fabushi:content:view 应名为「内容管理」, 实际命中 ${n:-?} 行"
fi
if query action_name "SELECT COUNT(*) FROM sys_action WHERE code = 'fabushi:content:view' AND name = '法布施内容管理页'"; then
  n="$(num "$Q")"
  [ "$n" = "1" ] && ok "action_name: 动作名已改为「法布施内容管理页」" \
                 || bad "action_name: 动作 fabushi:content:view 应名为「法布施内容管理页」, 实际命中 ${n:-?} 行"
fi

# ---------- ⑥ 行业词表 ----------
# 期望值从迁移文件解析(种子迁移本身"只补缺失、不覆盖"), 不在这里另抄一份。
ind_file="$(ls "$MIG_DIR" | grep -E '__industry_dict_seed\.sql$' | sort -t. -k2,2n -k3,3n -k4,4n | tail -1)"
labels=""
if [ -n "$ind_file" ]; then
  labels="$(sed -n '/JOIN (/,/) v/p' "$MIG_DIR/$ind_file" | sed -n "s/.*SELECT '\([^']*\)'.*/\1/p" | grep -v '^$' | sort -u)"
fi
label_n=0; in_list=""
for l in ${labels:-}; do
  case "$l" in *\'*) bad "industry_seed: 词表标签含单引号, 拒绝拼进 SQL: [${l}]"; label_n=-1; break ;; esac
  label_n=$((label_n + 1)); in_list="${in_list:+${in_list},}'${l}'"
done
if [ "$label_n" -le 0 ] && [ "$label_n" -ne -1 ]; then
  bad "industry_seed: 无法从迁移文件 ${ind_file:-<未找到>} 解析词表标签"
elif [ "$label_n" -gt 0 ] && query industry_seed "SELECT COUNT(DISTINCT d.dict_value) FROM sys_dict_data d JOIN sys_dict_type t ON d.dict_type_id = t.id WHERE t.dict_code = 'industry' AND d.dict_value IN (${in_list})"; then
  n="$(num "$Q")"
  [ "$n" = "$label_n" ] && ok "industry_seed: 行业词表 ${label_n} 个值都在" \
                        || bad "industry_seed: 行业词表应有 ${label_n} 个种子值, 实际 ${n:-?} 个"
fi

# ---------- ⑦ 首启向导标记 ----------
# 种子迁移只在"已有用户"时写标记; 全新安装(没有用户)不写是正确的。
users=""; flag=""; users_q=0; flag_q=0
query onboarding_users "SELECT COUNT(*) FROM sys_user" && { users="$(num "$Q")"; users_q=1; }
query onboarding_flag "SELECT config_value FROM sys_config WHERE config_key = 'onboarding.completed'" && { flag="$(printf '%s' "$Q" | tr -d '\r[:space:]')"; flag_q=1; }
if [ "$users_q" -eq 1 ] && [ "$flag_q" -eq 1 ]; then
  if [ -z "$users" ]; then bad "onboarding_flag: 无法解析用户数"
  elif [ "$users" -gt 0 ] && [ "$flag" = "true" ]; then ok "onboarding_flag: 存量安装(${users} 个用户)已写首启向导完成标记"
  elif [ "$users" -gt 0 ]; then bad "onboarding_flag: 有 ${users} 个用户却没有首启向导完成标记, 升级后所有人都会被带到向导页"
  # 断言守住的不变量是"升级后没有存量用户会被带到向导页"; 没有用户时它天然成立, 标记写没写都不影响本次迁移
  else ok "onboarding_flag: 没有用户(全新安装), 不存在会被带到向导页的存量用户(标记=[${flag:-未写}])"; fi
fi

# ---------- ⑧ 提醒频率 ----------
if query remind_freq_dict "SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON d.dict_type_id = t.id WHERE t.dict_code = 'fabushi_remind_freq' AND d.dict_value = 'workday'"; then
  n="$(num "$Q")"
  [ "$n" = "0" ] && ok "remind_freq_dict: 提醒频率字典没有 workday 残留" \
                 || bad "remind_freq_dict: 提醒频率字典仍有 ${n:-?} 个 workday"
fi
if query remind_freq_settings "SELECT COUNT(*) FROM biz_fabushi_user_setting WHERE freq_code = 'workday'"; then
  n="$(num "$Q")"
  [ "$n" = "0" ] && ok "remind_freq_settings: 用户提醒设置没有 workday 残留" \
                 || bad "remind_freq_settings: 仍有 ${n:-?} 条用户提醒设置是 workday"
fi

echo "══════════════════════════════════════════════════"
echo "迁移后断言结果: PASS=${PASS_N} FAIL=${FAIL_N} BLOCKED=${BLOCK_N}"
if [ "$FAIL_N" -gt 0 ]; then
  echo "❌ 迁移后断言未通过: 不要启动新后端, 按迁移前备份恢复后排查" >&2
  exit 1
elif [ "$BLOCK_N" -gt 0 ]; then
  echo "⛔ 迁移后断言未能全部核对(BLOCKED): 补齐前置条件后重跑, 本次不能当作通过" >&2
  exit 2
fi
echo "✅ 迁移后断言全部通过"
exit 0
