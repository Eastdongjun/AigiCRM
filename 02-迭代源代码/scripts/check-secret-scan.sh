#!/usr/bin/env bash
# 全量 secret 扫描(审计 F1, 2026-10-21): 扫描 02-迭代源代码 子树内 git 跟踪+未忽略文件,
# 检测疑似凭据字面量。此前 quality-gate 只对"变更文件"跑弱正则(password[:=]), 且
# aigicrm-ui/*.mjs 生产冒烟脚本不在任何扫描目标里 —— admin/NM005 口令因此入库(F1)。
#
# 设计约束:
#   1) 本脚本自身不得包含任何真实口令/密钥值 —— 只做"形态"检测: 模式用检测正则拼装,
#      不内嵌任何具体泄露值。
#   2) 值形态检测, 非对抗性防护: 含 $ 的行(shell/env/模板引用, 如 "$DB_PASSWORD")与
#      占位符行(redacted/placeholder/dummy/changeme)对 p1/p4/p5 豁免, 防误报;
#      哈希类(p6)不做占位符豁免, 只走 allowlist。
#   3) 已知历史物(bcrypt 种子哈希/扫描器测试夹具)走下方 ALLOWLIST, 每条必须带理由。
#   4) 豁免机制只认"精确路径+模式ID", 不支持通配, 防止宽豁免吞掉新泄露。
#
# 检测模式(值形态):
#   p1 password-assign               口令类变量赋值字面量(PASS/password/pwd/sshpass = '...')
#   p2 login-positional-literal-fill 登录表单位置填值字面量(.nth(N).fill('...')) —— F1 泄露类的直接回归防线
#   p3 private-key                   私钥文件头(拼装生成, 见 PK_HEAD/PK_TAIL)
#   p4 url-embedded-cred             URL userinfo 内嵌口令(scheme 登录凭据形态)
#   p5 secret-token-assign           secret/token/apiKey 赋值长字面量(≥16)
#   p6 bcrypt-hash                   bcrypt 口令哈希($2a$/$2b$/$2y$)
#
# 用法: bash scripts/check-secret-scan.sh [扫描根目录]   (缺省=本仓库 02-迭代源代码 子树; 测试可注入临时仓库)
# 退出码: 0=无未豁免命中; 1=存在未豁免命中; 2=环境缺失(非 git 仓库等, fail-closed)

set -u

SCRIPT_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_ROOT="$(cd "$SCRIPT_SELF_DIR/.." && pwd)"
SCAN_ROOT="${1:-$DEFAULT_ROOT}"

fail() { echo "FAIL: $*" >&2; exit 1; }
blocked() { echo "BLOCKED: $*" >&2; exit 2; }

command -v git >/dev/null 2>&1 || blocked "secret 扫描需要 git(枚举跟踪/未忽略文件), 未找到 —— fail-closed"
git -C "$SCAN_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  blocked "secret 扫描根目录不是 git 工作区: $SCAN_ROOT"

# ---- 检测模式(双引号串内 \" 转成字面 ", 供 ERE 使用; p3 头部拼装, 避免源码中出现完整密钥头字样) ----
P_PASSWORD_ASSIGN="(^|[^A-Za-z0-9_])(sshpass|pass(word)?|pwd)[\"']?[[:space:]]*[:=][[:space:]]*[\"'][^\"']{6,}[\"']"
P_POSITIONAL_FILL="nth\([0-9]+\)\.fill\([\"'][^\"']{4,}[\"']\)"
PK_HEAD="BEGIN (RSA |EC |DSA |OPENSSH )?"
PK_TAIL="PRIVATE KEY"
P_PRIVATE_KEY="${PK_HEAD}${PK_TAIL}"
P_URL_CRED="[a-z][a-z0-9+.-]*://[^/[:space:]\"':@]+:[^/[:space:]\"'@]{4,}@"
P_SECRET_TOKEN_ASSIGN="(^|[^A-Za-z0-9_])(secret|token|api[_-]?key)[\"']?[[:space:]]*[:=][[:space:]]*[\"'][A-Za-z0-9_./+=-]{16,}[\"']"
P_BCRYPT_HASH='\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}'
# 【T0 2026-11-11 审计整改】p7/p8: 测试口令默认值双形态——
#   p7=shell/env 默认值里内嵌口令样串(默认值含井号等强口令特征, 形态见 P_PASS_DEFAULT_EMBED);
#   p8=赋值字符串口令样(引号内 字母串+井号+字母数字 的强口令字面量, 形态见 P_PASS_LIKE_LITERAL)。
#   背景: e2e 脚本曾在参数默认值里硬编码测试口令而 p1-p6 全漏(整改后该口令已环境注入)。
#   注: 本文件注释一律用文字描述形态, 不写引号包裹的示例串(防自噬)。
P_PASS_DEFAULT_EMBED='\$\{[A-Za-z_][A-Za-z0-9_]*(PASS|PWD)[A-Za-z0-9_]*:-[^}]*[#][^}]*\}'
P_PASS_LIKE_LITERAL="[\"'][A-Za-z]{2,}#[A-Za-z0-9@#]{4,}[\"']"

# 模式表: id<TAB>正则<TAB>大小写(ci/cs)<TAB>豁免策略(exempt=对 $/占位符豁免; strict=只认 allowlist)
TAB=$'\t'
PATTERNS=(
  "p1${TAB}${P_PASSWORD_ASSIGN}${TAB}ci${TAB}exempt"
  "p2${TAB}${P_POSITIONAL_FILL}${TAB}cs${TAB}strict"
  "p3${TAB}${P_PRIVATE_KEY}${TAB}cs${TAB}strict"
  "p4${TAB}${P_URL_CRED}${TAB}cs${TAB}exempt"
  "p5${TAB}${P_SECRET_TOKEN_ASSIGN}${TAB}ci${TAB}exempt"
  "p6${TAB}${P_BCRYPT_HASH}${TAB}cs${TAB}strict"
  "p7${TAB}${P_PASS_DEFAULT_EMBED}${TAB}ci${TAB}strict"
  "p8${TAB}${P_PASS_LIKE_LITERAL}${TAB}cs${TAB}strict"
)

PLACEHOLDER_MARKERS='redacted|placeholder|dummy|changeme|change-me'

# ---- 豁免清单: 每行 "精确路径:模式ID 理由"(理由写在同一行第一个空白之后, 匹配只看首字段)。
#      新增条目必须写明为什么这不是活凭据。不支持通配, 防止宽豁免吞掉新泄露。
#      注: deploy/cleanroom/build/*.sql 含种子 bcrypt 但属 git-ignored 未跟踪文件, 不入扫描范围;
#      若日后被强制纳入跟踪, 须为其补显式条目或改为构建期生成。 ----
ALLOWLIST="
sql/migration/20260814/rbac-convergence-m1.sql:p6 冻结迁移中预置账号的 bcrypt 口令哈希(审计 F1 已知; 迁移契约冻结不可改, 口令轮换属运维)
aigicrm-backend/src/test/java/com/aigicrm/security/JwtAuthFilterTest.java:p5 单测自造 HMAC 签名密钥字面量(值自述 test-, 仅单测内使用, 非任何环境真实密钥)
aigicrm-backend/src/test/java/com/aigicrm/security/JwtTokenProviderTest.java:p5 单测自造 HMAC 签名密钥字面量(值自述 test-, 仅单测内使用, 非任何环境真实密钥)
aigicrm-backend/src/test/java/com/aigicrm/security/PermissionSecurityFixTest.java:p5 单测自造 HMAC 签名密钥字面量(值自述 test-, 仅单测内使用, 非任何环境真实密钥)
scripts/test-secret-scan.sh:p1 扫描器行为测试的伪凭据夹具(构造的假值, 非真实凭据)
scripts/test-secret-scan.sh:p2 扫描器行为测试的伪登录填值夹具(构造的假值, 非真实凭据)
scripts/test-secret-scan.sh:p4 扫描器行为测试的伪 URL 凭据夹具(构造的假值, 非真实凭据)
"

is_allowlisted() {
  printf '%s\n' "$ALLOWLIST" | awk -v key="$1:$2" 'NF && $1 == key { found=1 } END { exit !found }'
}

# ---- 枚举文件: git 跟踪 + 未跟踪未忽略(--exclude-standard 自动排除 node_modules 等), 再硬排除 node_modules/.git ----
file_list="$(git -C "$SCAN_ROOT" -c core.quotePath=false ls-files --cached --others --exclude-standard |
  grep -vE '(^|/)node_modules(/|$)|(^|/)\.git(/|$)' || true)"
if [ -z "$file_list" ]; then
  echo "PASS: secret 扫描范围无文件(显式通过; 若非预期请检查扫描根目录)"
  exit 0
fi

scanned=0
allowlisted_hits=0
violations=""

while IFS= read -r rel; do
  [ -n "$rel" ] || continue
  file="$SCAN_ROOT/$rel"
  [ -f "$file" ] || continue
  scanned=$((scanned + 1))
  # -I 跳过二进制; 大小写敏感全集 + 口令/密钥类键名补一遍大小写不敏感, 去重后逐模式归因
  cs_lines="$(grep -hnIE -e "$P_PASSWORD_ASSIGN" -e "$P_POSITIONAL_FILL" -e "$P_PRIVATE_KEY" \
                    -e "$P_URL_CRED" -e "$P_SECRET_TOKEN_ASSIGN" -e "$P_BCRYPT_HASH" \
                    -e "$P_PASS_DEFAULT_EMBED" -e "$P_PASS_LIKE_LITERAL" -- "$file" 2>/dev/null || true)"
  ci_lines="$(grep -ihnIE -e "$P_PASSWORD_ASSIGN" -e "$P_SECRET_TOKEN_ASSIGN" -e "$P_PASS_DEFAULT_EMBED" -- "$file" 2>/dev/null || true)"
  [ -n "$cs_lines$ci_lines" ] || continue
  all_lines="$(printf '%s\n%s\n' "$cs_lines" "$ci_lines" | grep -vE '^$' | sort -u)"
  while IFS= read -r hit; do
    [ -n "$hit" ] || continue
    line_no="${hit%%:*}"
    text="${hit#*:}"
    for spec in "${PATTERNS[@]}"; do
      IFS="$TAB" read -r pid regex case_mode exemption <<<"$spec"
      if [ "$case_mode" = "ci" ]; then
        shopt -s nocasematch
        [[ "$text" =~ $regex ]]
        matched=$?
        shopt -u nocasematch
      else
        [[ "$text" =~ $regex ]]
        matched=$?
      fi
      [ "$matched" -eq 0 ] || continue
      if [ "$exemption" = "exempt" ]; then
        case "$text" in
          *'$'*) continue ;;  # shell/env/模板引用值, 非字面量
        esac
        if printf '%s' "$text" | grep -qiE "$PLACEHOLDER_MARKERS"; then continue; fi
      fi
      if is_allowlisted "$rel" "$pid"; then
        allowlisted_hits=$((allowlisted_hits + 1))
        continue
      fi
      snippet="$(printf '%s' "$text" | cut -c1-160)"
      violations="${violations}${rel}:${line_no} [${pid}] ${snippet}
"
    done
  done <<<"$all_lines"
done <<<"$file_list"

total_allow="$(printf '%s\n' "$ALLOWLIST" | grep -c . )"

if [ -n "$violations" ]; then
  violation_count="$(printf '%s' "$violations" | grep -c . )"
  echo "FAIL: secret 扫描发现 ${violation_count} 处未豁免疑似凭据(共扫描 ${scanned} 个文件):" >&2
  printf '%s' "$violations" | sed 's/^/  /' >&2
  echo "  处置: 真实凭据→立即轮换并改环境变量读取; 确认为历史物/假值→在 ALLOWLIST 加精确条目并写明理由。" >&2
  exit 1
fi

echo "PASS: secret 扫描 ${scanned} 个文件, 未豁免命中 0(豁免命中 ${allowlisted_hits}, 豁免条目 ${total_allow}; p1-p6 形态检测见脚本头注释)。"
exit 0
