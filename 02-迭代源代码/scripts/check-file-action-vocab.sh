#!/bin/bash
# 【治本③ 2026-11-11】文件动作词表单一源对齐钉: 后端常量=权威, 前端矩阵/MP 消费必须与之一致。
# 背景: edit伞词→细词演进散在三端各持一份, 词表错位连出两个 bug(MP rename/ctx归组)。
# 规则:
#   1. 后端 P_* 常量 == 前端 FILE_ACTIONS∪FOLDER_ACTIONS (多词/少词即 FAIL)
#   2. MP buildFileRowView 的 includes('x') 消费词 ⊆ 后端词表 ∪ {edit 兼容白名单}
#   3. 'edit' 为历史兼容伞词: 凡消费处出现必须与 'rename' 并存(防只认旧词)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderPermissionService.java"
FSD="$ROOT/aigicrm-ui/src/views/system/FileSpaceDefaults.vue"
MP="$ROOT/aigicrm-miniprogram/pages/files/index.js"
fail() { echo "FAIL: $1" >&2; exit 1; }

# 后端权威词表: ALL 7 文件动作 + 夹动作(create/rename/delete)
BACKEND_FILE="view upload download rename replace share delete"
BACKEND_FOLDER="create rename delete"
for w in $BACKEND_FILE $BACKEND_FOLDER; do
  W=$(echo "$w" | tr '[:lower:]' '[:upper:]')
  grep -Eq "String P_${W} = " "$BACKEND" || fail "后端缺 P_${W} 常量(词表权威源漂移)"
done
# 后端不得擅自新增未知动作常量进 ALL_PERMS 视图(防词表悄悄膨胀)
grep -q "List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD, P_RENAME, P_REPLACE)" "$BACKEND" \
  || fail "后端 ALL_PERMS 词表变动——请同步本钉与三端词表(单一源纪律)"

# 前端矩阵词表
FE_FILE=$(python3 - "$FSD" << 'PY'
import re,sys
s=open(sys.argv[1]).read()
m=re.search(r'const FILE_ACTIONS = \[(.*?)\]', s, re.S)
print(' '.join(re.findall(r"value: '([a-z]+)'", m.group(1))) if m else '')
PY
)
[ "$FE_FILE" = "$BACKEND_FILE" ] || fail "前端 FILE_ACTIONS=[$FE_FILE] ≠ 后端[$BACKEND_FILE]——词表三端漂移"

# MP 消费词检查: includes 词都在词表∪{edit}; edit 必须与 rename 并存
MP_WORDS=$(grep -oE "folderPerms\.includes\('[a-z]+'\)" "$MP" | grep -oE "'[a-z]+'" | tr -d "'" | sort -u)
for w in $MP_WORDS; do
  case " $BACKEND_FILE $BACKEND_FOLDER edit " in
    *" $w "*) ;;
    *) fail "MP 消费了词表外动作 '$w'(folderPerms.includes)——先入词表再消费";;
  esac
done
if grep -q "folderPerms.includes('edit')" "$MP"; then
  grep -q "folderPerms.includes('rename')" "$MP" || fail "MP 消费 'edit' 兼容词但未与 'rename' 并存——旧词独占即词表错位回归"
fi
echo "PASS: 文件动作词表三端一致(后端权威=前端矩阵=MP消费∪{edit兼容并存})"
