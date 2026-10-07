#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MINI="$ROOT/aigicrm-miniprogram"
fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$MINI/app.json" ] || fail 'AIGICRM mini-program root is missing app.json'
[ -f "$MINI/project.config.json" ] || fail 'AIGICRM mini-program root is missing project.config.json'
python3 - "$MINI/app.json" "$MINI/project.config.json" <<'PY'
import json, sys
from pathlib import Path

app = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
project = json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'))
pages = app.get('pages', [])
# 【2026-10-07 钉随代码】1b3c865b 死页面清理(D 系列): file/cases×2/notification×2 五个
# 零入口页面连同磁盘文件删除 —— 46→41。钉按"注册页均有磁盘实体且被 navigation 契约覆盖"动态校验,
# 并保底下限防误删整段 pages。
import os
mini_root = os.path.dirname(sys.argv[1])
missing = [pg for pg in pages if not (
    os.path.exists(os.path.join(mini_root, pg + '.wxml')) and os.path.exists(os.path.join(mini_root, pg + '.js'))
)]
if missing:
    raise SystemExit(f'registered pages missing on disk: {missing}')
if not (30 <= len(pages) <= 46):
    raise SystemExit(f'page count {len(pages)} outside sanity band [30, 46]')
if project.get('compileType') != 'miniprogram':
    raise SystemExit('project.config.json compileType must be miniprogram')
if project.get('miniprogramRoot') != './':
    raise SystemExit('project.config.json miniprogramRoot must be ./')
# AppID 为 AIGICRM 小程序在微信公众平台的登记值（2026-09-13 由所有者确认）
if project.get('appid') != 'wx513e6dd7eabdae7b':
    raise SystemExit('project.config.json appid is not the registered AIGICRM appid')
PY
echo 'PASS: WeChat developer-tool project root is self-contained and AIGICRM-specific.'
