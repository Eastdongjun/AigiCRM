#!/usr/bin/env bash
set -euo pipefail

# 【CI 门 2026-11-12】本契约校验本机 VSCode 诊断导出(硬编码本机中央目录), 纯本地工具——
# CI runner 无此路径必炸。CI 的 contracts job 传 AIGI_SKIP_VSCODE=1 跳过(本地不传=照常跑)。
[ "${AIGI_SKIP_VSCODE:-0}" = "1" ] && { echo "SKIP: vscode-diagnostics 契约(CI 无本机诊断导出)"; exit 0; }

CENTRAL='/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/current'
RAW="$CENTRAL/vscode-diagnostics.raw.json"
NORMALIZED="$CENTRAL/vscode-diagnostics.json"
REQUEST="$CENTRAL/vscode-diagnostics.request.json"

RAW="$RAW" NORMALIZED="$NORMALIZED" REQUEST="$REQUEST" python3 - <<'PY'
import json
import os
from pathlib import Path

raw = json.loads(Path(os.environ['RAW']).read_text(encoding='utf-8'))
normalized = json.loads(Path(os.environ['NORMALIZED']).read_text(encoding='utf-8'))
request = json.loads(Path(os.environ['REQUEST']).read_text(encoding='utf-8'))

diagnostics = raw.get('diagnostics')
if not isinstance(diagnostics, list):
    raise SystemExit('FAIL: raw VS Code diagnostics are not a list')
if diagnostics:
    raise SystemExit(f'FAIL: central VS Code diagnostics contain {len(diagnostics)} issue(s)')
if normalized.get('status') != 'verified:no-vscode-diagnostics':
    raise SystemExit('FAIL: normalized diagnostics status is not verified:no-vscode-diagnostics')
if request.get('status') != 'completed':
    raise SystemExit('FAIL: VS Code diagnostics export request is not completed')
print('PASS: VS Code Problems export is current, completed, and issue-free.')
PY
