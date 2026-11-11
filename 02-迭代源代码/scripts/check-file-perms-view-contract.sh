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
rg -Fq "perms.some((p) => folderPerms.value.includes(p))" "$FILE_TABLE" || fail "PC canFileAction 未多词化(some)——单词消费回退"

# ② MP 双词(既有, 防回退)
rg -Fq "folderPerms.includes('edit') || folderPerms.includes('rename')" "$MP_FILES" || fail "MP files 改名门控双词兼容被移除"

# ③ FolderPanel 消费 folderActions 分字段
rg -Fq "folder.folderActions?.includes('rename')" "$FOLDER_PANEL" || fail "FolderPanel 夹改名未消费 folderActions 细词"
rg -Fq "folder.folderActions?.includes('delete')" "$FOLDER_PANEL" || fail "FolderPanel 夹删除未消费 folderActions 细词"
! rg -Fq "folder.perms?.includes('edit')" "$FOLDER_PANEL" || fail "FolderPanel 仍消费文件动作词 perms('edit') 判夹按钮——词义混叠回退"

# ④ 后端按钮视图输出细词
rg -Fq "P_VIEW, P_UPLOAD, P_DOWNLOAD, P_RENAME, P_REPLACE, P_SHARE, P_DELETE" "$FPS" || fail "permsForBatch 动作循环缺细词(rename/replace)"
rg -Fq "folderActionsForBatch" "$FPS" || fail "folderActionsForBatch 缺失——FOLDER 授权不进按钮视图(根因5回退)"

# ⑤ 无夹文件空间锚
rg -Fq "file.getFileScope() != null" "$FPS" || fail "checkFile 无夹文件 fileScope 空间锚被移除——跨空间越权回退(根因4)"

# ⑥ 树组装+根目录类型授权
rg -Fq "folder.setFolderActions" "$FSVC" || fail "FileService 树组装未回填 folderActions"
rg -Fq "folderActionsForBatch" "$FSVC" || fail "FileService 未调用 folderActionsForBatch"
rg -Fq 'GetMapping("/folders/type-perms")' "$FCTRL" || fail "空间根类型授权端点缺失(根因2回退)"
rg -Fq "getTypePerms('public')" "$USEFP" || fail "PC 根目录未消费类型授权接口(根因2回退)"

echo "PASS: 文件按钮视图词表契约(双词兼容/夹行分字段/空间锚/根目录类型授权) 全部就位。"
