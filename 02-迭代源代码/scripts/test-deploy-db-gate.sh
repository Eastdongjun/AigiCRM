#!/bin/bash
# 负例: 提取 deploy-db.sh 的 validate 判定逻辑, 模拟 docker run 失败(输出无 error/failed 关键词)
# 场景=A2 窗口真实故障(镜像拉取超时)——旧逻辑假绿, 新逻辑须 exit 1
set -uo pipefail
simulate_validate() {  # $1=rc $2=out
  local VALIDATE_RC="$1" VALIDATE_OUT="$2"
  if [ "$VALIDATE_RC" -ne 0 ] && ! echo "$VALIDATE_OUT" | grep -q 'checksum mismatch'; then
    echo "❌ validate 未执行成功(rc=$VALIDATE_RC)——拒绝继续"; return 1
  fi
  return 0
}
FAIL=0
# 用例1: docker 拉取超时(输出无关键词, rc=125) → 必须拒绝
simulate_validate 125 'docker: Get "https://registry-1.docker.io/v2/": net/http: request canceled while waiting for connection (Client.Timeout exceeded while awaiting headers). See docker run help.' \
  && { echo "FAIL: 假绿未修复(用例1)"; FAIL=1; } || echo "PASS: 用例1 拉取超时被拒"
# 用例2: checksum mismatch(rc=1) → 走 repair 分支(不在此拒)
simulate_validate 1 'Flyway Community Edition ... Successfully validated ... checksum mismatch detected' \
  && echo "PASS: 用例2 checksum 预期失败放行" || { echo "FAIL: 用例2 误拒"; FAIL=1; }
# 用例3: 成功(rc=0)
simulate_validate 0 'Successfully validated' && echo "PASS: 用例3 成功放行" || { echo "FAIL: 用例3 误拒"; FAIL=1; }
exit $FAIL
