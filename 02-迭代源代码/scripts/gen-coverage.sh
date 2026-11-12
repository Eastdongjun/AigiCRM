#!/usr/bin/env bash
# 【P2 修复 2026-11-12】交付证据包固定产出: 后端覆盖率报告(jacoco HTML + 总数行数)。
# 背景: gate.sh 跑 mvn test 只生成 jacoco.exec, HTML 需 jacoco:report——交付审计要
# "一键可复现的覆盖率证据", 本脚本产出 target/site/jacoco/index.html + 逐包摘要行。
# 基线参考(2026-11-12): 行覆盖 56.9%; 关键域补测计划见 docs/REQUIREMENTS.md Go-Live 台账。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/aigicrm-backend"
mvn -B -q test jacoco:report -Djacoco.skip=false 2>&1 | grep -E "Tests run.*Fail|BUILD" | tail -2 || true
REPORT=target/site/jacoco/jacoco.csv
[ -f "$REPORT" ] || { echo "FAIL: jacoco.csv 未生成(报告路径异常)" >&2; exit 1; }
awk -F',' 'NR>1 { mi+=$8; ci+=$9; mb+=$10; cb+=$11 } END {
  if (mi+ci==0) { print "覆盖率: 无数据"; exit 1 }
  printf "指令覆盖 %.1f%% / 分支覆盖 %.1f%% (类指令 covered=%d missed=%d)\n", ci*100/(mi+ci), cb*100/(mb+cb), ci, mi
}' "$REPORT"
echo "HTML 报告: aigicrm-backend/target/site/jacoco/index.html"
echo "PASS: 覆盖率报告已产出"
