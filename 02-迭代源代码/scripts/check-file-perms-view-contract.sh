#!/usr/bin/env bash
# 【修复 2026-11-12 根因1/2/5】文件按钮视图词表契约
#
# 背景: "类型默认授权勾选了, 目标用户前端看不到"——后端 permsForBatch 输出权限终版细词
#   (rename/replace), PC FileTable 只认历史伞词 edit → 授权对象改名/替换按钮恒不渲染;
#   根目录 perms 前端硬编码使类型授权在公共根无效; 夹行 FOLDER 授权从不进按钮视图。
# 本契约钉住三端"视图消费词 ⊇ 后端输出词"的关键形态, 单词回退/字段混叠即红:
#   ① PC FileTable canRename/canReplace 双词('edit'∨细词) —— 与 MP files:290 同构
#   ② MP files 页改名门控双词(防回退)
#   ③ PC FolderPanel 夹行按钮消费 folderActions 分字段(防与文件动作词义混叠)
#   ④ 后端 permsForBatch 输出含 P_RENAME/P_REPLACE 细词
#   ⑤ 后端 checkFile 无夹文件 fileScope 空间锚(防跨空间越权回退)
#   ⑥ FileService 树组装回填 folderActions + type-perms 端点存在
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }

# 【P1 修复 2026-11-12】负例自证通道(AIGI_NEGATIVE=1): 不动真源码——FileTable 沙箱副本回退为
# 单词门控(根因1 形态), ①组断言在沙箱上必须红; 证明契约"改坏会红"而非只验"正例不红"。
if [ "${AIGI_NEGATIVE:-0}" = "1" ]; then
  NEG_DIR=$(mktemp -d /tmp/t9neg-fpv.XXXXXX); trap 'rm -rf "$NEG_DIR"' EXIT
  cp "$ROOT/aigicrm-ui/src/views/file/FileTable.vue" "$NEG_DIR/FileTable.vue"
  python3 - "$NEG_DIR/FileTable.vue" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]); t = p.read_text()
t2 = t.replace("canFileAction(row, 'file:edit', 'edit', 'rename')", "canFileAction(row, 'file:edit', 'edit')")
assert t2 != t, "变异未生效: 沙箱中未找到双词形态"
p.write_text(t2)
PY
  FILE_TABLE="$NEG_DIR/FileTable.vue"
  if rg -Fq "canFileAction(row, 'file:edit', 'edit', 'rename')" "$FILE_TABLE"; then
    fail "[负例自证] 沙箱回退单词门控后断言未红——检测器失效"
  fi
  echo "FAIL: [负例自证] PC 改名门控已回退单词形态(沙箱变异)——契约正确抓红" >&2
  exit 1
fi

FILE_TABLE="$ROOT/aigicrm-ui/src/views/file/FileTable.vue"
MP_FILES="$ROOT/aigicrm-miniprogram/pages/files/index.js"
FOLDER_PANEL="$ROOT/aigicrm-ui/src/views/file/FolderPanel.vue"
FPS="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderPermissionService.java"
FSVC="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java"
FCTRL="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java"
USEFP="$ROOT/aigicrm-ui/src/composables/useFilePage.js"

# ① PC 双词兼容
rg -Fq "canFileAction(row, 'file:edit', 'edit', 'rename')" "$FILE_TABLE" || fail "PC 改名门控未双词兼容(edit∨rename)——根因1回退"
rg -Fq "canFileAction(row, 'file:edit', 'edit', 'replace')" "$FILE_TABLE" || fail "PC 替换门控未双词兼容(edit∨replace)——根因1回退"
rg -Fq "perms.some((p) => src.includes(p))" "$FILE_TABLE" || fail "PC canFileAction 未多词化(some)——单词消费回退"

# ② MP 双词+行级优先(既有双词钉随 T2-MP 升级为 actionPerms 形态)
rg -Fq "actionPerms.includes('edit') || actionPerms.includes('rename')" "$MP_FILES" || fail "MP files 改名门控双词兼容被移除"
rg -Fq "const rowPerms = Array.isArray(item.perms) ? item.perms : null" "$MP_FILES" || fail "MP files 行级 row.perms 优先消费缺失(T2-MP)"

# ③ FolderPanel 消费 folderActions 分字段
rg -Fq "folder.folderActions?.includes('rename')" "$FOLDER_PANEL" || fail "FolderPanel 夹改名未消费 folderActions 细词"
rg -Fq "folder.folderActions?.includes('delete')" "$FOLDER_PANEL" || fail "FolderPanel 夹删除未消费 folderActions 细词"
! rg -Fq "folder.perms?.includes('edit')" "$FOLDER_PANEL" || fail "FolderPanel 仍消费文件动作词 perms('edit') 判夹按钮——词义混叠回退"

# ④ 后端按钮视图输出细词
rg -Fq "P_VIEW, P_UPLOAD, P_DOWNLOAD, P_RENAME, P_REPLACE, P_SHARE, P_DELETE" "$FPS" || fail "permsForBatch 动作循环缺细词(rename/replace)"
rg -Fq "folderActionsForBatch" "$FPS" || fail "folderActionsForBatch 缺失——FOLDER 授权不进按钮视图(根因5回退)"

# ⑤ 无夹文件空间锚
rg -Fq "file.getFileScope() != null" "$FPS" || fail "checkFile 无夹文件 fileScope 空间锚被移除——跨空间越权回退(根因4)"

# ⑥ 树组装+根目录行级类型授权(T2: row.perms 方案)
rg -Fq "folder.setFolderActions" "$FSVC" || fail "FileService 树组装未回填 folderActions"
rg -Fq "folderActionsForBatch" "$FSVC" || fail "FileService 未调用 folderActionsForBatch"
rg -Fq "permsForFiles(page.getRecords(), userId)" "$FSVC" || fail "FileService 列表未回填行级 row.perms(根因2回退)"
rg -Fq "Array.isArray(row?.perms) ? row.perms : folderPerms.value" "$FILE_TABLE" || fail "PC 行级优先消费缺失(row.perms)"
! rg -Fq 'GetMapping("/folders/type-perms")' "$FCTRL" || fail "已退役的页级 type-perms 端点复活(T2 已由行级取代)"

echo "PASS: 文件按钮视图词表契约(双词兼容/夹行分字段/空间锚/根目录行级类型授权) 全部就位。"
