#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT/docs/mobile-manual-qa-checklist.md"
fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$DOC" ] || fail 'mobile manual QA checklist is missing'
grep -Fq '/Users/dongjun/东来/项目/01-进行中/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram' "$DOC" || fail 'QA checklist must name the real mini-program root'
grep -Fq 'app.json' "$DOC" || fail 'QA checklist must require app.json at the imported root'
grep -Fq 'project.config.json' "$DOC" || fail 'QA checklist must require project.config.json at the imported root'
grep -Fq 'scripts/check-wechat-project-root.sh' "$DOC" || fail 'QA checklist must run the project-root preflight'
grep -Fq '使用测试账号' "$DOC" || fail 'QA checklist must require a real test account'
grep -Fq '不能替代' "$DOC" || fail 'QA checklist must distinguish automation from manual evidence'
echo 'PASS: mobile manual QA checklist is bound to the real AIGICRM project root.'
