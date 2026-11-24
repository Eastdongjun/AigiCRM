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
# 【2026-10-27 生产事故】ServerAlive 心跳必配: 远端 docker build 挂死(0 CPU)时连接不会断,
# 本地脚本死等假象=ssh挂——心跳让死连接 ~60s 内暴露; 与远端命令自身 timeout 配套。
AIGICRM_SSH_OPTS_STR="-o BatchMode=yes -o ConnectTimeout=${SSH_CONNECT_TIMEOUT:-20} -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$KNOWN_HOSTS_FILE -o ServerAliveInterval=15 -o ServerAliveCountMax=4"
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
    ssh_prod_once "$@" && return 0
    _rc=$?
    sleep 2
  done
  return "$_rc"
}
# 测试通道：公钥优先（【D4-3 2026-10-21】TEST_KEY 配置即走 -i 公钥——BatchMode 下 ssh 不做
# 密码认证, 且本机 sshpass 为 expect 包装器不支持 -e, 口令通道实测不可达；此为整改报告
# §4.3「给测试服配公钥」的落法）。TEST_KEY 缺席时保留 sshpass -e 密码回退（真 sshpass 机器可用）。
ssh_test_once() {
  if [ -n "${TEST_KEY:-}" ]; then
    if [ ! -r "$TEST_KEY" ]; then
      echo "错误: TEST_KEY 不存在或不可读: $TEST_KEY" >&2
      return 1
    fi
    ssh -i "$TEST_KEY" ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "${TEST_USER}@${TEST_HOST}" "$@"
    return $?
  fi
  if [ -z "${TEST_PASS:-}" ]; then
    echo "错误: 缺少 TEST_PASS 或 TEST_KEY（请在部署 env 配置）" >&2
    return 1
  fi
  SSHPASS="$TEST_PASS" sshpass -e ssh ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "${TEST_USER}@${TEST_HOST}" "$@"
}
ssh_test() {
  local _rc=0 _i
  for _i in 1 2 3; do
    ssh_test_once "$@" && return 0
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
    scp_prod_once "$@" && return 0
    _rc=$?
    sleep 2
  done
  return "$_rc"
}
# 【D4-3 2026-10-21】scp 测试通道同 ssh_test_once：TEST_KEY 公钥优先, 缺席才走 sshpass -e
scp_test_once() {
  if [ -n "${TEST_KEY:-}" ]; then
    if [ ! -r "$TEST_KEY" ]; then
      echo "错误: TEST_KEY 不存在或不可读: $TEST_KEY" >&2
      return 1
    fi
    scp -i "$TEST_KEY" ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "$@"
    return $?
  fi
  if [ -z "${TEST_PASS:-}" ]; then
    echo "错误: 缺少 TEST_PASS 或 TEST_KEY（请在部署 env 配置）" >&2
    return 1
  fi
  SSHPASS="$TEST_PASS" sshpass -e scp ${AIGICRM_SSH_OPTS[@]+"${AIGICRM_SSH_OPTS[@]}"} "$@"
}
scp_test() {
  local _rc=0 _i
  for _i in 1 2 3; do
    scp_test_once "$@" && return 0
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
  # 【ops 瑕疵③修复 2026-10-21】checkend 成功时本就静默(仅在临期/失败时输出), 旧实现
  # 捕获输出后以 [ -z "$leaf" ] 判空 → 有效证书必被误判"无法读取"。改为纯管道退出码判定
  # (x509 解析失败或已过期均非零)。真连生产首跑实证: 链校验通过而此步恒败。
  if ! printf '' | openssl s_client -connect "$host:$port" -servername "$host" -showcerts 2>/dev/null \
      | openssl x509 -noout -checkend 0 >/dev/null 2>&1; then
    echo "❌ $host 证书已过期或无法读取叶子证书(到期检查失败)" >&2
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

# ============================================================
# 【GL-06 2026-11-24】FILE_GRANT_MODE 必须显式设置(fail-closed)
#
# 背景: biz_file_grant 主链模式(env + 重启生效)取值 legacy / dual / new, 生产现值为 new。
#   deploy-backend.sh 的旧写法是 ${FILE_GRANT_MODE:-legacy} —— 部署 env 里只要没写这一项,
#   下一次部署或健康检查失败后的自动回滚, 就会把生产静默降回 legacy(新引擎整体旁路), 且日志里看不出来。
# 本函数在本地侧、任何远端动作之前拒绝"未设置"和"取值非法"。
# 合法取值与 FileGrantService 的 file.grant.mode 一致; 新增取值时同步这里。
# 用法: 会启动后端容器的脚本在远端动作之前调用(调用方 set -e, 返回 1 即中止)。
# ============================================================
assert_file_grant_mode() {
  local v="${FILE_GRANT_MODE:-}"
  if [ -z "$v" ]; then
    echo "❌ FILE_GRANT_MODE 未设置：拒绝部署/回滚。生产现值为 new，请在部署 env 显式写 FILE_GRANT_MODE=new（脚本不再默认 legacy）" >&2
    return 1
  fi
  case "$v" in
    legacy|dual|new) ;;
    *)
      echo "❌ FILE_GRANT_MODE 取值非法: '$v'（只接受 legacy / dual / new）" >&2
      return 1
      ;;
  esac
  # 变量后紧跟全角标点时必须用 ${v} —— macOS bash 3.2 不把全角字符当作变量名边界(见 assert_prod_env_pinned 的同类说明)
  echo "✅ FILE_GRANT_MODE=${v}（已显式设置）"
  return 0
}

# ============================================================
# 【GL-09 2026-11-24】制品溯源(fail-closed): 生产部署的制品必须等于冻结清单登记的那个版本
#
# 背景:
#   deploy-backend.sh 部署的是"测试服容器里正在跑的那个 jar"。它只校验这个 jar 像个 jar、上传前后一致,
#     从不核对它是不是冻结的版本; 部署日志里的 commit 记的是本机当前 HEAD, 与 jar 实际由哪个提交构建无关。
#   deploy-frontend.sh 用本机工作区直接构建, 有未提交改动也照发。
#   两者的 md5/sha256 都只证明"传上去的和本地的一致", 不证明"本地的就是冻结的那个"。
#
# 冻结清单: RELEASE_MANIFEST(部署 env 里给出文件路径), JSON, 由冻结步骤生成:
#   {
#     "schema": 1,
#     "tag": "rc/…",
#     "commit": "<40 位提交号>",
#     "backend":  { "jar":  { "md5": "<32 位>", "sha256": "<64 位>" } },
#     "frontend": { "entry": { "file": "assets/index-….js", "sha256": "<64 位>" } },
#     "miniprogram": { "version": "…" }, "migrations": 167, "api_endpoints": 350
#   }
# 前端取入口 JS 而不是 index.html: index.html 在部署时会被注入 prefetch、写入时间戳, 每次都不同;
# 入口 JS 由构建决定, 同一份源码与依赖构建出来是同一份。
#
# 四道门, 都在任何生产远端动作之前, 缺一不可(调用方 set -e, 返回 1 即中止):
#   assert_release_manifest    清单存在、可解析, 登记的提交等于当前 HEAD
#   assert_worktree_clean      已跟踪文件没有未提交改动。未跟踪文件不拦: 它们不属于提交内容;
#                              会影响构建产物的那些(例如被 gitignore 的本地配置)由制品指纹那道门兜住
#   assert_backend_artifact    jar 的 md5 与 sha256 等于清单
#   assert_frontend_artifact   构建出的入口 JS 的文件名与 sha256 等于清单
# 没有旁路开关: 急修也走同一条路 —— 给急修的提交出一份清单再部署。
# 行为测试见 scripts/test-release-provenance.sh。
# ============================================================
RELEASE_MANIFEST="${RELEASE_MANIFEST:-}"

manifest_field() { # $1=点分字段名(如 backend.jar.md5); 取不到时说明原因并返回 1
  [ -n "$RELEASE_MANIFEST" ] || { echo "冻结清单未提供: 部署 env 里没有 RELEASE_MANIFEST(冻结清单文件路径)" >&2; return 1; }
  [ -f "$RELEASE_MANIFEST" ] || { echo "冻结清单文件不存在: $RELEASE_MANIFEST" >&2; return 1; }
  python3 - "$RELEASE_MANIFEST" "$1" <<'PY'
import json, sys
path, keys = sys.argv[1], sys.argv[2].split('.')
try:
    doc = json.load(open(path, encoding='utf-8'))
except (OSError, ValueError) as exc:
    print('冻结清单无法读取或不是合法 JSON: %s' % exc, file=sys.stderr); sys.exit(1)
if not isinstance(doc, dict) or doc.get('schema') != 1:
    print('冻结清单格式不对: 应为 JSON 对象且 schema 为 1', file=sys.stderr); sys.exit(1)
cur, seen = doc, []
for key in keys:
    seen.append(key)
    if not isinstance(cur, dict) or key not in cur:
        print('冻结清单缺少字段: %s' % '.'.join(seen), file=sys.stderr); sys.exit(1)
    cur = cur[key]
if not isinstance(cur, str) or not cur:
    print('冻结清单字段不是非空字符串: %s' % '.'.join(keys), file=sys.stderr); sys.exit(1)
print(cur)
PY
}

_source_root() { (cd "${SOURCE_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}" && pwd); }

assert_release_manifest() {
  local want have
  want="$(manifest_field commit)" || { echo "❌ 生产部署必须提供冻结清单, 并登记要部署的提交。拒绝继续, 未发生任何远端动作" >&2; return 1; }
  want="$(printf '%s' "$want" | tr 'A-Z' 'a-z')"
  case "$want" in *[!0-9a-f]*|'') echo "❌ 冻结清单里的提交号不是十六进制: [${want}]" >&2; return 1 ;; esac
  [ "${#want}" -eq 40 ] || { echo "❌ 冻结清单里的提交号应为 40 位, 实际 ${#want} 位: [${want}]" >&2; return 1; }
  have="$(cd "$(_source_root)" && git rev-parse HEAD 2>/dev/null | tr 'A-Z' 'a-z' || echo unknown)"
  if [ "$want" != "$have" ]; then
    echo "❌ 当前 HEAD(${have}) 不是冻结清单登记的提交(${want})。生产只能部署冻结的那个提交: 请先检出冻结的提交(或它的 tag)" >&2
    return 1
  fi
  echo "✅ 冻结清单: 登记的提交 ${want} 与当前 HEAD 一致"
}

assert_worktree_clean() {
  local dirty
  dirty="$(cd "$(_source_root)" && git status --porcelain --untracked-files=no 2>/dev/null)"
  if [ -n "$dirty" ]; then
    echo "❌ 工作区有未提交的改动 —— 生产只能部署已提交且已冻结的内容。先提交或还原这些改动:" >&2
    printf '%s\n' "$dirty" | head -10 >&2
    return 1
  fi
  echo "✅ 工作区干净(已跟踪文件没有未提交改动)"
}

assert_backend_artifact() { # $1=从测试服取回的 jar
  local jar="$1" want_md5 want_sha have_md5 have_sha
  want_md5="$(manifest_field backend.jar.md5)"    || { echo "❌ 冻结清单没有登记后端 jar 的 md5, 无法核对" >&2; return 1; }
  want_sha="$(manifest_field backend.jar.sha256)" || { echo "❌ 冻结清单没有登记后端 jar 的 sha256, 无法核对" >&2; return 1; }
  have_md5="$(md5 -q "$jar")"
  have_sha="$(shasum -a 256 "$jar" | awk '{print $1}')"
  if [ "$have_md5" != "$want_md5" ] || [ "$have_sha" != "$want_sha" ]; then
    echo "❌ 从测试服取回的 jar 不是冻结清单登记的那个版本, 拒绝部署:" >&2
    echo "    md5    清单=${want_md5}  实际=${have_md5}" >&2
    echo "    sha256 清单=${want_sha}" >&2
    echo "           实际=${have_sha}" >&2
    echo "    测试服上跑的可能不是冻结的版本; 核对后再部署" >&2
    return 1
  fi
  echo "✅ 后端 jar 与冻结清单一致(md5=${have_md5})"
}

assert_frontend_artifact() { # $1=构建产物目录(dist)
  local dist="$1" entry want_file want_sha have_sha
  entry="$(grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' "$dist/index.html" 2>/dev/null | head -1)"
  if [ -z "$entry" ] || [ ! -s "$dist/$entry" ]; then
    echo "❌ 构建产物没有入口 JS(index.html 应引用 assets/index-*.js)" >&2
    return 1
  fi
  want_file="$(manifest_field frontend.entry.file)"  || { echo "❌ 冻结清单没有登记前端入口文件, 无法核对" >&2; return 1; }
  want_sha="$(manifest_field frontend.entry.sha256)" || { echo "❌ 冻结清单没有登记前端入口的 sha256, 无法核对" >&2; return 1; }
  have_sha="$(shasum -a 256 "$dist/$entry" | awk '{print $1}')"
  if [ "$entry" != "$want_file" ] || [ "$have_sha" != "$want_sha" ]; then
    echo "❌ 构建出的前端不是冻结清单登记的那个版本, 拒绝部署:" >&2
    echo "    入口文件 清单=${want_file}" >&2
    echo "            实际=${entry}" >&2
    echo "    sha256   清单=${want_sha}" >&2
    echo "            实际=${have_sha}" >&2
    echo "    常见原因: 依赖或构建环境变了, 或有被 gitignore 的本地配置影响了构建" >&2
    return 1
  fi
  echo "✅ 前端入口与冻结清单一致(${entry})"
}

# ============================================================
# 【GL-05 2026-11-24】后端运行时的单一出处
#
# 背景: "构建镜像 / 启动容器 / 等待就绪"这几个远端函数此前在四个地方各抄一份, 彼此已经漂移:
#   - deploy-backend.sh 正向路径带 FILE_GRANT_MODE、导出目录及其挂载;
#   - 同一脚本"健康检查失败后的自动回滚"漏了这三项 —— 回滚后的容器与正向部署的容器行为不同;
#   - rollback.sh 写死 DB_HOST=mysql / REDIS_HOST=redis(生产容器名是 <slug>-mysql / <slug>-redis),
#     同样漏了这三项, 而且远端脚本根本没收到 DEPLOY_DIR。
# 行为测试见 scripts/test-deploy-backend-runtime.sh: 渲染结果放进 docker 桩里真实执行, 逐项断言。
#
# 用法(本地侧): 把本函数的输出拼进要发给远端的脚本即可, 两个入口脚本的写法都能用:
#   { echo 'set -Eeuo pipefail'; <输出变量赋值>; emit_remote_backend_runtime; cat <<'REMOTE' … REMOTE; } | ssh …
# 远端需要事先定义的变量:
#   必需: APP_SLUG DB_NAME APP_DB_USER APP_DB_PASSWORD JWT_SECRET OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET
#         OSS_BUCKET_NAME FILE_GRANT_MODE BACKEND_TAG
#   调用 build_image / fast_rollback_container 时另需 DEPLOY_DIR; 调用 rollback_to_backup 时另需 BACKUP_JAR。
# 改后端容器的启动参数只改这里。
# ============================================================
emit_remote_backend_runtime() {
  cat <<'REMOTE_BACKEND_RUNTIME'
build_image() {
  cd "$DEPLOY_DIR"
  # 【2026-10-27 生产事故】buildkit 在生产 dockerd 上 0 CPU 挂死(连极小构建都挂, 4 次实证),
  # 容器运行时正常——改经典构建器(DOCKER_BUILDKIT=0)并加 240s 硬超时, 挂死可暴露而非死等。
  if ! timeout 240 env DOCKER_BUILDKIT=0 docker build -q -t "${APP_SLUG}-backend:${BACKEND_TAG}" -f Dockerfile-run . >/tmp/dbuild.out 2>&1; then
    tail -5 /tmp/dbuild.out >&2
    return 1
  fi
  tail -3 /tmp/dbuild.out >&2 || true
}
image_jar_md5() {
  docker run --rm --entrypoint sh "${APP_SLUG}-backend:${BACKEND_TAG}" -c 'md5sum /app/app.jar' 2>/dev/null \
    | cut -d' ' -f1 | tr -d '\r\n '
}
start_backend() {
  # FILE_GRANT_MODE 必须由本地侧显式传入(本地已用 assert_file_grant_mode 校验取值), 不回落 legacy。
  # 这一步放在写含机密的 env-file 之前: 检查失败时不在 /tmp 留下任何文件。
  if [ -z "${FILE_GRANT_MODE:-}" ]; then
    echo "❌ FILE_GRANT_MODE 未传入, 拒绝启动后端" >&2
    return 1
  fi
  docker stop "${APP_SLUG}-backend" >/dev/null 2>&1 || true
  docker rm "${APP_SLUG}-backend" >/dev/null 2>&1 || true
  # 【D3 2026-10-21】机密(DB/JWT/OSS 口令)经 0600 env-file 注入，不进 docker run argv
  #（旧写法把明文口令作为 -e KEY=VALUE 参数写进远端 docker 进程 argv，同机 ps 可见）；非机密项保持 -e 便于排查。
  local envf mounts
  envf="$(mktemp /tmp/aigicrm-backend-env.XXXXXX)"
  chmod 600 "$envf"
  {
    printf 'DB_PASSWORD=%s\n' "$APP_DB_PASSWORD"
    printf 'JWT_SECRET=%s\n' "$JWT_SECRET"
    printf 'OSS_ACCESS_KEY_ID=%s\n' "$OSS_ACCESS_KEY_ID"
    printf 'OSS_ACCESS_KEY_SECRET=%s\n' "$OSS_ACCESS_KEY_SECRET"
  } > "$envf"
  if ! docker run -d --name "${APP_SLUG}-backend" --network "${APP_SLUG}_default" --restart always \
    -p 127.0.0.1:8080:8080 \
    --env-file "$envf" \
    -e DB_HOST="${APP_SLUG}-mysql" -e DB_PORT=3306 -e DB_NAME="$DB_NAME" -e DB_USER="$APP_DB_USER" \
    -e REDIS_HOST="${APP_SLUG}-redis" -e REDIS_PORT=6379 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e FILE_GRANT_MODE="$FILE_GRANT_MODE" \
    -e EXPORT_DIR=/app/exports \
    -e TENANT_ENABLE=true \
    -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
    -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_BUCKET_NAME="$OSS_BUCKET_NAME" \
    -e CORS_ORIGINS=https://www.aigicrm.com \
    -v /Aigicrm/files/uploads:/app/uploads \
    -v /Aigicrm/files/ycmn-exports:/app/exports \
    -v "${APP_SLUG}-logs:/app/logs" \
    "${APP_SLUG}-backend:${BACKEND_TAG}" >/dev/null; then
    rm -f "$envf"
    echo "❌ docker run 后端容器失败" >&2
    return 1
  fi
  rm -f "$envf"
  # 挂载自检：上传目录与导出目录都必须在 NAS。少任何一个,"回滚后的容器"都会与"正向部署的容器"行为不同
  # (此前只有正向部署检查上传挂载; 两条回滚路径既不检查上传、也完全没有导出挂载)。
  mounts="$(docker inspect "${APP_SLUG}-backend" --format '{{range .Mounts}}{{.Source}} {{end}}')"
  case "$mounts" in
    *'/Aigicrm/files/uploads '*) ;;
    *) echo "❌ 上传挂载不是 NAS 路径 /Aigicrm/files/uploads" >&2; return 1 ;;
  esac
  case "$mounts" in
    *'/Aigicrm/files/ycmn-exports '*) ;;
    *) echo "❌ 导出挂载不是 NAS 路径 /Aigicrm/files/ycmn-exports" >&2; return 1 ;;
  esac
}
wait_started() {
  local i
  for i in $(seq 1 60); do
    if [ "$(docker inspect -f '{{.State.Running}}' "${APP_SLUG}-backend" 2>/dev/null)" = "true" ] \
       && docker logs "${APP_SLUG}-backend" 2>&1 | grep 'Started AigicrmApplication' >/dev/null; then
      return 0
    fi
    sleep 5
  done
  echo "❌ 后端 300 秒内未就绪" >&2
  docker logs "${APP_SLUG}-backend" --tail 50 >&2 2>&1 || true
  return 1
}
fast_rollback_container() {
  # 【2026-11-17 build 通道挂死事故】免重建快滚: docker cp 直换容器内 jar + 重启。
  # 救急通道——容器内 jar 与镜像脱钩(镜像内 jar 陈旧), 标准通道恢复后需重建自愈;
  # 容器已被删除时无救(无 cp 载体), 只能走镜像重建。
  docker inspect "${APP_SLUG}-backend" >/dev/null 2>&1 \
    || { echo "❌ 快滚通道不可用: 容器不存在(需镜像重建)" >&2; return 1; }
  docker cp "$DEPLOY_DIR/app.jar" "${APP_SLUG}-backend:/app/app.jar" || return 1
  local exp act
  exp=$(md5sum "$DEPLOY_DIR/app.jar" | cut -d' ' -f1 | tr -d '\r\n ')
  act=$(docker exec "${APP_SLUG}-backend" md5sum /app/app.jar 2>/dev/null | cut -d' ' -f1 | tr -d '\r\n ')
  [ "$exp" = "$act" ] || { echo "❌ 快滚后容器内 jar 校验不符(exp=$exp act=$act)" >&2; return 1; }
  docker restart "${APP_SLUG}-backend" >/dev/null || return 1
  wait_started || return 1
}
rollback_to_backup() {
  [ -s "$BACKUP_JAR" ] || { echo "❌ 备份 jar 不存在/为空: $BACKUP_JAR" >&2; return 1; }
  echo "↩ 使用备份 jar 回滚: $BACKUP_JAR" >&2
  cp -p "$BACKUP_JAR" "$DEPLOY_DIR/app.jar" || return 1
  if build_image; then
    local exp act
    exp=$(md5sum "$BACKUP_JAR" | cut -d' ' -f1 | tr -d '\r\n ')
    act=$(image_jar_md5)
    [ "$exp" = "$act" ] || { echo "❌ 回滚镜像内 jar 校验不符(exp=$exp act=$act)" >&2; return 1; }
    start_backend || return 1
    wait_started || return 1
  else
    # 镜像重建不可用(dockerd 构建挂死等) → 免重建快滚, 保服务恢复优先
    echo "↩ 镜像重建失败, 走免重建快滚(docker cp + restart) ..." >&2
    fast_rollback_container || return 1
  fi
}
REMOTE_BACKEND_RUNTIME
}
