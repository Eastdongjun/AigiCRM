#!/bin/bash
# AIGICRM 部署公共库: 配置加载 + ssh/scp 封装 + 健康检查
# 注意: 库文件不设 errexit(调用方自行控制); 仅 unset 变量与管道检查
set -uo pipefail

# 加载部署配置/凭据(不入 git)。2026-09-11 起统一为 ~/.aigicrm-deploy.env（原 ~/.ycmn-deploy.env 兜底已移除）
ENV_FILE="${AIGICRM_DEPLOY_ENV:-${DEPLOY_ENV_FILE:-$HOME/.aigicrm-deploy.env}}"
if [ ! -f "$ENV_FILE" ]; then
  echo "错误: 缺少 $ENV_FILE (复制 scripts/aigicrm-deploy.env.example 并填写)" >&2
  exit 1
fi
source "$ENV_FILE"

APP_NAME="${APP_NAME:-AIGICRM}"
APP_SLUG="${APP_SLUG:-aigicrm}"
DB_NAME="${DB_NAME:-aigicrm}"
DEPLOY_DIR="${DEPLOY_DIR:-/root/aigicrm}"
DEPLOY_DATA_DIR="${DEPLOY_DATA_DIR:-/data/aigicrm}"
# 【IL2·2026-10-08 审查修复 + 复核遗留延伸】生产后端镜像 tag 单一来源：
# deploy-backend.sh（build/md5 门禁/docker run 三处）与 rollback.sh（回滚重建两处）共用引用，
# 升版只改这一行——此前 2.7.0 在两脚本共五处硬编码，漏改任一处会让"构建的镜像"与
# "门禁校验/重启/回滚使用的镜像"指向不同对象（门禁校验错对象，或部署新版、回滚旧 tag）。
BACKEND_TAG="${BACKEND_TAG:-2.7.0}"

# 生产实际部署目录(实测 2026-09-23:容器 WorkingDir=/data/ycmn,jar+compose 均在此;
# 旧默认 /root/aigicrm 为历史残留,env 未显式覆盖时以实测值为准)
if [ "${DEPLOY_DIR:-}" = "/root/aigicrm" ] || [ -z "${DEPLOY_DIR:-}" ]; then
  DEPLOY_DIR="/data/ycmn"
fi

# DB 凭据必须存在（禁止脚本内硬编码，一律经 env 注入）
#
# 【2026-09-12】仅测试环境的脚本（如 deploy-db-test.sh）**不需要**生产 DB_PASSWORD，
#   而本库此前无条件强制它 ⇒ 只想跑测试库迁移的人被迫先持有生产口令。
#   故增加显式逃生：调用方在 source 本库**之前**置
#       AIGICRM_TEST_ONLY=1
#   即跳过"生产库口令/应用账号"类校验（其余能力不变，prod 脚本未置该变量，行为完全不变）。
if [ "${AIGICRM_TEST_ONLY:-0}" = "1" ]; then
  :
else
  [ -n "${DB_PASSWORD:-}" ] || { echo "错误: 缺少 DB_PASSWORD（请在 $ENV_FILE 配置）" >&2; exit 1; }
fi

# 冒烟登录凭据(可选) —— 值在部署 env (HEALTH_CHECK_USER/HEALTH_CHECK_PASS)，禁止硬编码入仓。
# 【2026-09-23】check_health 已改为无凭据探活(见下)，部署门禁不再依赖本组变量；
# 仅供 smoke-test / check-prod-perf 等深度诊断工具使用。注意 admin 初口令首登强制改密后即失效，
# 须填"当前有效"口令(或专用冒烟账号)，失效时深度工具自行降级，不再阻断部署。
HEALTH_CHECK_USER="${HEALTH_CHECK_USER:-admin}"
HEALTH_CHECK_PASS="${HEALTH_CHECK_PASS:-}"

# ============================================================
# 【D4 2026-10-21 审计修复】SSH 通道加固（旧默认关闭主机密钥校验 + sshpass -p 密码回退）
#   - 生产/备份/数据库维护通道一律强制 StrictHostKeyChecking=yes + BatchMode=yes + 固定 known_hosts；
#   - OpenSSH 对同一参数取"第一个获得的值"：若把 =yes 追加在用户 SSH_OPTS(=no) 之后**不会生效**。
#     因此这里不透传用户 SSH_OPTS 中的校验类参数，而是剔除后由脚本重建（剔除会打印告警）；
#   - 生产通道移除 sshpass 密码回退：仅公钥(PROD_KEY)，缺失即 fail-closed，不再静默回落密码通道；
#   - 测试通道仍可密码登录，但一律 SSHPASS=<val> sshpass -e（口令经 env，不进 argv，本机 ps 不可见）。
#
# known_hosts 固定：默认 ~/.ssh/known_hosts，可用 AIGICRM_KNOWN_HOSTS 指向项目内钉死指纹的文件。
# 首次采集（离线核对指纹后）：
#   ssh-keyscan -t ed25519,ecdsa,rsa <PROD_HOST> <TEST_HOST> >> ~/.ssh/known_hosts
# 文件缺失即失败——不接受交互式首次确认（BatchMode 下也无法交互），避免"看似修好实则未校验"。
# ============================================================
KNOWN_HOSTS_FILE="${AIGICRM_KNOWN_HOSTS:-$HOME/.ssh/known_hosts}"
if [ ! -f "$KNOWN_HOSTS_FILE" ]; then
  echo "错误: known_hosts 文件不存在: $KNOWN_HOSTS_FILE" >&2
  echo "  先离线核对主机指纹后固定: ssh-keyscan -t ed25519,ecdsa,rsa <host> >> <file>" >&2
  echo "  并设置 AIGICRM_KNOWN_HOSTS=<file>（缺省 ~/.ssh/known_hosts）。在指纹落地前拒绝连接(fail-closed)。" >&2
  exit 1
fi

# 清洗用户 SSH_OPTS：主机密钥校验类参数(-o StrictHostKeyChecking/BatchMode/UserKnownHostsFile/
# PasswordAuthentication)由脚本强制钉死，用户值**成对**剔除并告警(逐词剔除会把 -o 与其参数拆散，
# 留下孤立的 -o 使 ssh 直接报错)。保留项追加在强制项之后——OpenSSH 同参数取第一个值，强制项在前。
SSH_OPTS="${SSH_OPTS:-}"
AIGICRM_SSH_OPTS_STR="-o BatchMode=yes -o ConnectTimeout=${SSH_CONNECT_TIMEOUT:-20} -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$KNOWN_HOSTS_FILE"
if [ -n "$SSH_OPTS" ]; then
  _DROPPED=""
  _KEPT=""
  # shellcheck disable=SC2206 # 需要按词拆分逐对检查
  _WORDS=($SSH_OPTS)
  _I=0
  while [ "$_I" -lt "${#_WORDS[@]}" ]; do
    _W="${_WORDS[$_I]}"
    case "$_W" in
      -o)
        _NV="${_WORDS[$((_I+1))]:-}"
        case "$_NV" in
          StrictHostKeyChecking=*|BatchMode=*|UserKnownHostsFile=*|PasswordAuthentication=*)
            _DROPPED="${_DROPPED:+$_DROPPED }$_NV" ;;
          *) _KEPT="$_KEPT -o $_NV" ;;
        esac
        _I=$((_I+2)) ;;
      # 粘连写法 -oKey=Value 同样剔除
      -oStrictHostKeyChecking=*|-oBatchMode=*|-oUserKnownHostsFile=*|-oPasswordAuthentication=*)
        _DROPPED="${_DROPPED:+$_DROPPED }${_W#-o}"
        _I=$((_I+1)) ;;
      *)
        _KEPT="$_KEPT $_W"
        _I=$((_I+1)) ;;
    esac
  done
  if [ -n "$_DROPPED" ]; then
    echo "⚠️ SSH_OPTS 中的主机密钥校验类选项已被忽略(脚本强制 StrictHostKeyChecking=yes + BatchMode + UserKnownHostsFile): $_DROPPED" >&2
  fi
  # 去除多余空白后追加(路径含空格的 -o 值不被支持，见上方 known_hosts 说明)
  [ -n "$_KEPT" ] && AIGICRM_SSH_OPTS_STR="$AIGICRM_SSH_OPTS_STR$(printf '%s' "$_KEPT")"
  unset _DROPPED _KEPT _WORDS _I _W _NV
fi
AIGICRM_SSH_OPTS=($AIGICRM_SSH_OPTS_STR)

# 单次 ssh(不做重试)。供需要 stdin 的脚本(printf | ssh_prod_once "bash -s")与
# 轮询类调用使用——带重试的 ssh_prod 在第一次尝试已消费 stdin, 重试时会读到 EOF。
# 【D4】仅公钥认证：PROD_KEY 必须由部署 env 显式注入(不再有写死的本机路径默认值，
# 换机器不再静默回退密码通道)；缺失/不可读即 fail-closed。
ssh_prod_once() {
  if [ -z "${PROD_KEY:-}" ]; then
    echo "错误: 缺少 PROD_KEY（请在部署 env 显式注入私钥路径；密码通道已按 D4 移除）" >&2
    return 1
  fi
  if [ ! -r "$PROD_KEY" ]; then
    echo "错误: PROD_KEY 不存在或不可读: $PROD_KEY" >&2
    return 1
  fi
  ssh -i "$PROD_KEY" ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "${PROD_USER}@${PROD_HOST}" "$@"
}

# ssh 封装带重试(连接建立类瞬时失败自动重试; 3 次后放弃)。
# 注: 重试仅对"连接未建立"的失败安全——绝大多数瞬时失败(连接重置/拒绝)发生在此阶段, 远程命令未执行;
# 部署类命令自身幂等(备份覆盖/rm -rf 先行/容器重启), 重试不会造成重复副作用。
ssh_prod() {
  local _rc=0 _i
  for _i in 1 2 3; do
    if ssh_prod_once "$@"; then return 0; fi
    _rc=$?
    sleep 2
  done
  return "$_rc"
}
# 测试通道：密码登录经 SSHPASS env + sshpass -e（口令不进 argv；旧 sshpass -p "$TEST_PASS" 写法已移除）
ssh_test_once() {
  if [ -z "${TEST_PASS:-}" ]; then
    echo "错误: 缺少 TEST_PASS（请在部署 env 配置）" >&2
    return 1
  fi
  SSHPASS="$TEST_PASS" sshpass -e ssh ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "${TEST_USER}@${TEST_HOST}" "$@"
}
ssh_test() {
  local _rc=0 _i
  for _i in 1 2 3; do
    if ssh_test_once "$@"; then return 0; fi
    _rc=$?
    sleep 2
  done
  return "$_rc"
}
scp_prod_once() {
  if [ -z "${PROD_KEY:-}" ] || [ ! -r "$PROD_KEY" ]; then
    echo "错误: PROD_KEY 缺失或不可读（scp 生产通道仅公钥；密码回退已按 D4 移除）" >&2
    return 1
  fi
  scp -i "$PROD_KEY" ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "$@"
}
scp_prod() {
  local _rc=0 _i
  for _i in 1 2 3; do
    if scp_prod_once "$@"; then return 0; fi
    _rc=$?
    sleep 2
  done
  return "$_rc"
}
scp_test_once() {
  if [ -z "${TEST_PASS:-}" ]; then
    echo "错误: 缺少 TEST_PASS（请在部署 env 配置）" >&2
    return 1
  fi
  SSHPASS="$TEST_PASS" sshpass -e scp ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "$@"
}
scp_test() {
  local _rc=0 _i
  for _i in 1 2 3; do
    if scp_test_once "$@"; then return 0; fi
    _rc=$?
    sleep 2
  done
  return "$_rc"
}

# 【D3/D6 辅助】curl 走系统 CA 校验；如部署机 CA 不含生产链，用 AIGICRM_CACERT 显式指定 CA 包。
# 不得再使用 -k（D6：证书过期/域不符/被替换必须使门禁失败）。
CURL_CA_OPTS=""
[ -n "${AIGICRM_CACERT:-}" ] && CURL_CA_OPTS="--cacert $AIGICRM_CACERT"

# ============================================================
# 【D6 2026-10-21 审计修复】生产 TLS 证书预检（只读，不连接后端、不带凭据）
# 去 curl -k 之前必须先证明部署机能以系统 CA 完整校验 www.aigicrm.com 证书链；
# 且证书过期不得被门禁放过。链校验失败 = 硬失败：先修链/固定 CA(AIGICRM_CACERT)，
# 不允许为了过门禁保留 -k。各部署/回滚脚本在**破坏性动作之前**调用本函数。
# ============================================================
check_prod_tls() {
  local host="${1:-www.aigicrm.com}" port="${2:-443}" out
  command -v openssl >/dev/null 2>&1 \
    || { echo "❌ TLS 预检需要 openssl（本机未安装）" >&2; return 1; }
  if ! out="$(printf '' | openssl s_client -connect "$host:$port" -servername "$host" -verify_return_error 2>&1)"; then
    echo "❌ TLS 链校验失败($host:$port)——先修证书链或用 AIGICRM_CACERT 固定 CA，不得以 -k 绕过" >&2
    printf '%s\n' "$out" | grep -E 'verify error|Verify return|alert' | head -5 >&2 || true
    return 1
  fi
  if ! printf '%s\n' "$out" | grep -q 'Verify return code: 0 (ok)'; then
    echo "❌ TLS 校验返回码非 ok($host:$port):" >&2
    printf '%s\n' "$out" | grep 'Verify return' >&2 || true
    return 1
  fi
  # 到期检查：已过期=硬失败；14 天内到期=告警(不阻断，但必须有人跟进续期)
  local leaf
  leaf="$(printf '' | openssl s_client -connect "$host:$port" -servername "$host" -showcerts 2>/dev/null | openssl x509 -noout -checkend 0 2>/dev/null)"
  if [ "$?" -ne 0 ] || [ -z "$leaf" ]; then
    echo "❌ 无法读取/解析 $host 证书(到期检查失败)" >&2
    return 1
  fi
  if ! printf '' | openssl s_client -connect "$host:$port" -servername "$host" -showcerts 2>/dev/null \
      | openssl x509 -noout -checkend 1209600 >/dev/null 2>&1; then
    echo "⚠️  $host 证书将在 14 天内到期，请立即安排续期（本次不阻断）" >&2
  fi
  echo "✅ TLS 证书校验通过 ($host:$port, 系统 CA 链完整, 未过期)"
  return 0
}

# 生产健康检查(【2026-09-23 重设计】无凭据探活):
# 旧法=真实登录，admin 初口令首登强制改密后必 401 假失败；且失败登录会写安全审计并累计
# IP 限流计数(AuthService.login)，密集部署日反复探测可能误触爆破告警——探活不应有副作用。
# 新法=GET /api/system/configs/public(SecurityConfig permitAll 的匿名只读接口，直查 DB)：
#   一次请求覆盖 DNS→TLS→nginx /api 转发→后端→MySQL 全链，零凭据零副作用。
#   判据=HTTP 200 且 body 含 "code":200 —— body 断言防 nginx 把 /api 误路由到 SPA 静态回退时
#   返回 200 index.html 的假阳性(公网 /actuator/health 实测正是这种回退，不能用作探活)。
check_health() {
  local base="${1:-https://www.aigicrm.com}"
  local resp code
  # 【D6 2026-10-21】去掉 -k：证书过期/域不符/被替换必须让健康检查失败。
  # 部署机 CA 不含生产链时用 AIGICRM_CACERT 显式指定（部署前 check_prod_tls 预检会先暴露此问题）。
  resp=$(curl -s $CURL_CA_OPTS --connect-timeout 10 --max-time 20 -w "\n%{http_code}" "$base/api/system/configs/public")
  code="${resp##*$'\n'}"
  if [ "$code" = "200" ] && printf '%s' "$resp" | grep -qE '"code":[[:space:]]*200'; then
    echo "✅ 健康检查通过 (公共配置接口 200: nginx→后端→DB 全链可达)"
    return 0
  fi
  echo "❌ 健康检查失败 (公共配置接口 HTTP $code, body: $(printf '%s' "$resp" | head -c 200))" >&2
  return 1
}

# 健康检查重试包装（默认 3 次）：单次网络抖动不应触发后端自动回滚；
# 3 次均失败仍返回非零，由调用方执行回滚/中止。
check_health_retry() {
  local attempts="${1:-3}" i
  for i in $(seq 1 "$attempts"); do
    if check_health; then return 0; fi
    if [ "$i" -lt "$attempts" ]; then
      echo "  健康检查失败，5 秒后重试 ($i/$attempts)..." >&2
      sleep 5
    fi
  done
  return 1
}

# 等待后端就绪(有限次数, 不再无限 until):
#   - 容器必须处于 running;
#   - 日志出现 Started AigicrmApplication。
# 超时返回非零并打印日志尾部, 由调用方决定回滚/中止。
wait_backend_started() {
  local attempts="${1:-60}" interval="${2:-5}" i
  for i in $(seq 1 "$attempts"); do
    if ssh_prod_once "set -e; [ \"\$(docker inspect -f '{{.State.Running}}' ${APP_SLUG}-backend 2>/dev/null)\" = 'true' ]; docker logs ${APP_SLUG}-backend 2>&1 | grep -q 'Started AigicrmApplication'" >/dev/null 2>&1; then
      ssh_prod_once "docker logs ${APP_SLUG}-backend 2>&1 | grep -E 'Started AigicrmApplication' | tail -1" || true
      return 0
    fi
    echo "  等待后端启动... ($i/$attempts)"
    sleep "$interval"
  done
  echo "❌ 后端在 $((attempts * interval)) 秒内未就绪 (容器未运行或未见 Started 日志)" >&2
  ssh_prod_once "docker logs ${APP_SLUG}-backend --tail 50 2>&1" >&2 || true
  return 1
}

# 部署架构自检(2026-08-15 磁盘架构迁移后强制执行):
# 标准布局 = 系统盘仅OS / 数据盘 /data(docker根+部署目录+数据库, 库与程序路径区分) / NAS=文件
# 【ops-3 2026-10-17】自检改为 fail-closed：任一关键项不符即返回非零，调用方(set -e)中止部署。
# 旧实现只打印 ⚠️ 且恒返回 0，架构偏航时部署仍继续，属 fail-open。
# 临时例外（需负责人确认）可显式设 AIGICRM_ALLOW_ARCH_DEVIATION=1 降级为告警。
check_deploy_arch() {
  local out
  echo "=== 部署架构自检(标准见 docs/生产部署规范.md) ==="
  if ! out="$(ssh_prod_once "APP_SLUG='$APP_SLUG' DEPLOY_DIR='$DEPLOY_DIR' DEPLOY_DATA_DIR='$DEPLOY_DATA_DIR' bash -s" <<'ARCH_CHECK'
R=$(docker info --format "{{.DockerRootDir}}" 2>/dev/null)
[ "$R" = "/data/docker" ] && echo "✅ docker 根=/data/docker" || echo "⚠️ docker 根=$R (标准: /data/docker)"
M=$(docker inspect ${APP_SLUG}-mysql --format "{{range .Mounts}}{{.Source}} {{end}}" 2>/dev/null | tr " " "\n" | grep -c "^/data/database/mysql-data$")
[ "$M" = "1" ] && echo "✅ mysql 数据=/data/database/mysql-data" || echo "⚠️ mysql 数据不在 /data/database/mysql-data"
L=$(readlink "$DEPLOY_DIR" 2>/dev/null)
[ "$L" = "$DEPLOY_DATA_DIR" ] && echo "✅ 部署目录软链 $DEPLOY_DIR → $DEPLOY_DATA_DIR" || echo "⚠️ $DEPLOY_DIR 非 $DEPLOY_DATA_DIR 软链 (当前: $L)"
U=$(docker inspect ${APP_SLUG}-backend --format "{{range .Mounts}}{{.Source}} {{end}}" 2>/dev/null | tr " " "\n" | grep -c "^/Aigicrm/files/uploads$")
[ "$U" = "1" ] && echo "✅ 上传文件=NAS /Aigicrm/files/uploads" || echo "⚠️ 上传文件不在 NAS"
ARCH_CHECK
  )"; then
    echo "❌ 部署架构自检无法执行(ssh/docker 失败)" >&2
    return 1
  fi
  printf '%s\n' "$out"
  # 输出完整性门禁：4 项检查必须都有结果行（✅ 或 ⚠️）；空输出/远端脚本未执行不得被当成通过
  if [ "$(printf '%s' "$out" | grep -Ec '^(✅|⚠️)')" -lt 4 ]; then
    echo "❌ 部署架构自检输出不完整（期望 4 项检查结果）" >&2
    return 1
  fi
  if printf '%s' "$out" | grep -q '⚠️'; then
    if [ "${AIGICRM_ALLOW_ARCH_DEVIATION:-0}" = "1" ]; then
      echo "⚠️ 部署架构存在偏差，但已显式设置 AIGICRM_ALLOW_ARCH_DEVIATION=1，继续执行" >&2
      return 0
    fi
    echo "❌ 部署架构自检失败：上述 ⚠️ 项必须修正（临时例外需显式 AIGICRM_ALLOW_ARCH_DEVIATION=1）" >&2
    return 1
  fi
  return 0
}

# ============================================================
# 【2026-09-12 新增】生产前置硬门禁：防止"改名未执行 → 部署静默空转"
#
# 背景（PROD-RENAME-PLAN-NEXT-VERSION.md §0 记录的发布阻断项）：
#   ~/.aigicrm-deploy.env 的 APP_SLUG / DB_NAME / DEPLOY_DIR / NAS_DIST_DIR
#   当前是**目标态**（aigicrm 系），而生产实际仍是 ycmn 系（改名窗口未开）。
#   若在此状态下直接跑生产部署脚本，会发生**静默空转** —— 最危险的一处是
#   deploy-frontend.sh：`mkdir -p $NAS_BACKUP_DIR/dist-rollback-...` 会**新建**目标目录，
#   随后 `rm -rf $NAS_DIST_DIR/* && tar xzf ... -C $NAS_DIST_DIR` 在一个
#   **新建的空目录**里"成功"完成，而生产实际服务目录毫无变化 —— 脚本却报成功。
#
# 本门禁在**任何破坏性动作之前**断言"env 指向的路径与生产实际一致"，
# 不一致则 fail-closed 退出，并给出两种解除方式。
#
# 用法：在各 prod 部署脚本的破坏性动作之前调用 assert_prod_env_pinned。
# 仅做字符串比较，无副作用、不接触网络。
# ============================================================
assert_prod_env_pinned() {
  local expected_slug="ycmn" expected_db="ycmn" expected_dir="/root/ycmn"
  local expected_dist="/Aigicrm/files/ycmn-dist" expected_data="/data/ycmn"
  local bad=0

  [ "${APP_SLUG:-}" = "$expected_slug" ] || {
    echo "❌ PROD_ENV_MISMATCH: APP_SLUG='${APP_SLUG:-}' 但生产实际是 '$expected_slug'" >&2; bad=1; }
  [ "${DB_NAME:-}" = "$expected_db" ] || {
    echo "❌ PROD_ENV_MISMATCH: DB_NAME='${DB_NAME:-}' 但生产实际是 '$expected_db'" >&2; bad=1; }
  [ "${DEPLOY_DIR:-}" = "$expected_dir" ] || {
    echo "❌ PROD_ENV_MISMATCH: DEPLOY_DIR='${DEPLOY_DIR:-}' 但生产实际是 '$expected_dir'" >&2; bad=1; }
  [ "${NAS_DIST_DIR:-}" = "$expected_dist" ] || {
    echo "❌ PROD_ENV_MISMATCH: NAS_DIST_DIR='${NAS_DIST_DIR:-}' 但生产实际是 '$expected_dist'" >&2; bad=1; }
  [ "${DEPLOY_DATA_DIR:-}" = "$expected_data" ] || {
    echo "⚠️  PROD_ENV_NOTE: DEPLOY_DATA_DIR='${DEPLOY_DATA_DIR:-}'，生产记录为 '$expected_data'（软链目标，请确认）" >&2; }

  if [ "$bad" -ne 0 ]; then
    cat >&2 <<'EOF'

⛔ 已中止：部署 env 指向"改名后的目标态"，而生产尚未改名。
   继续执行会造成**静默空转**（最坏情形：前端脚本在新建的空目录里"成功"，
   生产实际目录未变，脚本却报成功）。

   两种解除方式（二选一）：
   (A) 钉回生产现值（推荐，改名另排窗口）—— 在 ~/.aigicrm-deploy.env 中设置：
         APP_SLUG=ycmn
         DB_NAME=ycmn
         DEPLOY_DIR=/root/ycmn
         DEPLOY_DATA_DIR=/data/ycmn
         NAS_DIST_DIR=/Aigicrm/files/ycmn-dist
   (B) 先执行改名窗口（见 PROD-RENAME-PLAN-NEXT-VERSION.md §4 runbook），
       改名完成后再把上述值切回 aigicrm 系。

   参考：PROD-RENAME-PLAN-NEXT-VERSION.md §0 / §2 / §5
EOF
    echo "PROD_ENV_GUARD=FAIL" >&2
    return 1
  fi

  # 注意：变量后紧跟全角标点时必须用 ${VAR} —— macOS bash 3.2 不会把全角字符
  # 当作变量名边界，$VAR）会被解析成变量名 "VAR）" 而报 unbound variable。
  echo "✅ 生产 env 已与生产现值一致（APP_SLUG=${APP_SLUG} DB_NAME=${DB_NAME} DEPLOY_DIR=${DEPLOY_DIR} NAS_DIST_DIR=${NAS_DIST_DIR}）"
  return 0
}


# 当前源码 commit(用于部署记录)
git_commit() {
  cd "${SOURCE_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}" && git rev-parse --short HEAD 2>/dev/null || echo "unknown"
}
