#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$ROOT/../AIGICRM.code-workspace"

WORKSPACE="$WORKSPACE" ROOT="$ROOT" python3 - <<'PY'
import json
import os
import re
from pathlib import Path

root = Path(os.environ['ROOT']).resolve()
workspace = Path(os.environ['WORKSPACE']).resolve()
data = json.loads(workspace.read_text(encoding='utf-8'))
paths = {item['path'] for item in data.get('folders', [])}
required = {'.', '02-迭代源代码'}
if not required.issubset(paths):
    raise SystemExit(f'FAIL: AIGICRM workspace folders missing: {sorted(required - paths)}')
if not (root / 'aigicrm-ui').is_dir() or not (root / 'aigicrm-backend').is_dir():
    raise SystemExit('FAIL: workspace source roots are missing')

settings = data.get('settings', {})
output_path = settings.get('donglaiDiagnosticsExporter.outputPath')
request_path = settings.get('donglaiDiagnosticsExporter.requestPath')
if not output_path:
    raise SystemExit('FAIL: VS Code diagnostics exporter outputPath is not configured')
if not request_path:
    raise SystemExit('FAIL: VS Code diagnostics exporter requestPath is not configured')
central_current = Path('/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/current')
if Path(output_path).resolve() != (central_current / 'vscode-diagnostics.raw.json').resolve():
    raise SystemExit('FAIL: VS Code diagnostics outputPath must use the AIGICRM central current channel')
if Path(request_path).resolve() != (central_current / 'vscode-diagnostics.request.json').resolve():
    raise SystemExit('FAIL: VS Code diagnostics requestPath must use the AIGICRM central current channel')

extensions = json.loads((root / '.vscode' / 'extensions.json').read_text(encoding='utf-8'))
if 'sonarsource.sonarlint-vscode' not in extensions.get('recommendations', []):
    raise SystemExit('FAIL: SonarQube for IDE recommendation is missing')

settings_text = (root / '.vscode' / 'settings.json').read_text(encoding='utf-8')
settings_text = re.sub(r'^\s*//.*$', '', settings_text, flags=re.MULTILINE)
settings_text = re.sub(r'\s+//.*$', '', settings_text, flags=re.MULTILINE)
settings_text = re.sub(r',\s*([}\]])', r'\1', settings_text)
local_settings = json.loads(settings_text)
if local_settings.get('problems.decorations.enabled') is not True:
    raise SystemExit('FAIL: VS Code Problems decorations are not enabled')
if local_settings.get('problems.autoReveal') is not True:
    raise SystemExit('FAIL: VS Code Problems autoReveal is not enabled')
if local_settings.get('sonarlint.disableTelemetry') is not True:
    raise SystemExit('FAIL: SonarLint telemetry is not disabled')

print('PASS: AIGICRM VS Code workspace, Problems, and free SonarLint contract is aligned.')
PY
