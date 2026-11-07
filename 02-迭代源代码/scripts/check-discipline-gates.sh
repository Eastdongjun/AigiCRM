#!/bin/bash
# 【2026-11-07 工程纪律守则·机器执法层】复盘 25 条错误固化为可机判契约。
#   守则 = docs/工程纪律守则-20261107.md;  复盘 = out/项目复盘-错误清单-20261107.md
# 本脚本自身遵守 T1(只认退出码): FAILS 计数 + 显式 exit, 无任何 || echo PASS 兜底;
# 同时被 §7 反钉在 dev-verify.sh 的 contracts 清单里(防"守卫存在但无执行入口"复盘教训)。
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAILS=0
fail() { echo "FAIL: $1"; FAILS=$((FAILS+1)); }
ok()   { echo "PASS: $1"; }

echo "── §1 [T1] 契约脚本禁 fail-open 判定 ──"
# 复盘#15: 历史写法"grep FAIL 后用 || 回显绿字"在脚本崩溃(TypeError)时不含 FAIL 字样 → 整晚假绿。
# 绿只能来自退出码; 竖杠兜底回显 GREEN/PASS/OK 一律禁止(注释行豁免, 本脚本注释用描述性措辞避开自匹配)。
failopen=""
for f in "$ROOT"/scripts/check-*.sh; do
  m=$(grep -nE '\|\|[[:space:]]*echo[[:space:]].*(GREEN|PASS|OK)' "$f" 2>/dev/null | grep -vE '^[0-9]+:[[:space:]]*#' || true)
  if [ -n "$m" ]; then failopen="$failopen
  $(basename "$f"): $(echo "$m" | head -2 | tr '\n' ' | ')"; fi
done
if [ -n "$failopen" ]; then
  fail "发现 fail-open 判定写法(绿不允许来自竖杠兜底):$failopen"
else
  ok "check-*.sh 无 fail-open 兜底判定写法"
fi

echo "── §2 [T1] 契约脚本必须有显式判定出口 ──"
# 隐式继承末命令状态 = 判定链可被 echo 等无害命令掐断成恒 0。
noexit=""
for f in "$ROOT"/scripts/check-*.sh; do
  if ! grep -qE 'exit |exec |sys\.exit|SystemExit' "$f"; then noexit="$noexit $(basename "$f")"; fi
done
if [ -n "$noexit" ]; then
  fail "以下契约脚本无显式判定出口(exit/exec/sys.exit/SystemExit):$noexit"
else
  ok "全部 check-*.sh 有显式判定出口"
fi

echo "── §3 [T3] 迁移禁硬编码行 id 定位 DML ──"
# 复盘#2: 硬编码 WHERE id= 误删 task 权限行 → 全员 403。数据操作必须按业务码定位。
# 已应用迁移属冻结历史不可改, 白名单豁免(仅 1 例: V2026.09.21.1, 2026-09-21 已应用)。
mighits=$(rg -U -n '(?s)(UPDATE|DELETE)\b[^;]{0,600}?WHERE\s+id\s*=\s*\d+' \
  "$ROOT/aigicrm-backend/src/main/resources/db/migration" -g '*.sql' 2>/dev/null \
  | rg -v 'V2026\.09\.21\.1' || true)
if [ -n "$mighits" ]; then
  fail "迁移出现硬编码行 id 的 UPDATE/DELETE(必须按业务码定位; 冻结白名单: V2026.09.21.1):
$mighits"
else
  ok "迁移无新增硬编码行 id DML(冻结白名单 1 例: V2026.09.21.1)"
fi

echo "── §4 [T2/T11] 部署脚本铁律钉 ──"
# 复盘#4: Dockerfile COPY 错位 → 两周陈旧 jar; 根修 = md5 硬门禁(c8c0fd6)。钉住防根修被拆。
if [ "$(grep -c 'md5' "$ROOT/scripts/deploy-backend.sh")" -ge 2 ]; then
  ok "deploy-backend.sh md5 门禁在位"
else
  fail "deploy-backend.sh md5 门禁消失(复盘#4 防复活钉)"
fi
# 复盘#18: deploy-db-test 默认迁错废弃旧库 aigicrm(84 支)。钉住正确默认目标库。
if grep -q 'aigicrm_prod_20261016' "$ROOT/scripts/deploy-db-test.sh" 2>/dev/null; then
  ok "deploy-db-test.sh 目标库默认断言在位"
else
  fail "deploy-db-test.sh 丢失 aigicrm_prod_20261016 目标库默认(复盘#18 防复活钉)"
fi
# 复盘#18: --network host 连不上无宿主端口的 shared-mysql; 注释行豁免。
netresidue=""
for d in "$ROOT"/scripts/deploy-db.sh "$ROOT"/scripts/deploy-db-test.sh; do
  if grep -v '^\s*#' "$d" 2>/dev/null | grep -q -- '--network host\|--network=host'; then netresidue="$netresidue $(basename "$d")"; fi
done
if [ -n "$netresidue" ]; then
  fail "部署脚本出现活代码 --network host:$netresidue(复盘#18 防复活钉)"
else
  ok "deploy-db*.sh 无活代码 --network host"
fi
# 复盘#5: yml 重复 spring 键 → 25 分钟中断; 根修 = ProfileYamlParseTest(f04ca9ac)。
if [ -f "$ROOT/aigicrm-backend/src/test/java/com/aigicrm/architecture/ProfileYamlParseTest.java" ]; then
  ok "ProfileYamlParseTest 在位(yml 结构校验)"
else
  fail "ProfileYamlParseTest 被删(yml 结构校验防线, 复盘#5 防复活钉)"
fi

echo "── §5 [T6] 凭据不入仓 ──"
# 复盘#13: 生产凭据明文入仓(settings.local.json)+SSH 私钥入备份。git 追踪清单必须零命中。
credhits=$(git -C "$ROOT" ls-files | rg -n 'settings\.local\.json|\.pem$|\.key$|(^|/)deploy\.env$|(^|/)\.env$' || true)
if [ -n "$credhits" ]; then
  fail "git 追踪了疑似凭据文件(密钥只走环境变量/密管):
$credhits"
else
  ok "git 追踪清单无凭据文件(settings.local.json/*.pem/*.key/deploy.env/.env)"
fi

echo "── §6 [T10] 嵌套 wx:for 必须显式别名(基线对账) ──"
# 复盘#12: 照片墙 wx:for index 遮蔽 → 跨客户 403。嵌套循环内层必须显式 item+index。
if node "$ROOT/scripts/check-nested-wxfor.mjs" "$ROOT"; then
  :
else
  fail "嵌套 wx:for 出现新增违例(见上方明细)"
fi

echo "── §7 [T1 自执法] 本门禁已登记执行入口 ──"
# 复盘教训: 四份契约曾"守卫存在但无任何执行入口"(git log -S 为证)。门禁必须被套件引用。
if grep -q 'check-discipline-gates.sh' "$ROOT/scripts/dev-verify.sh"; then
  ok "check-discipline-gates.sh 已登记进 dev-verify.sh contracts 清单"
else
  fail "check-discipline-gates.sh 未登记进 dev-verify.sh(守卫无执行入口=不存在)"
fi

echo "──"
if [ "$FAILS" -eq 0 ]; then
  echo "DISCIPLINE GREEN: 七段纪律执法全过"
  exit 0
else
  echo "DISCIPLINE RED: $FAILS 段违例"
  exit "$FAILS"
fi
