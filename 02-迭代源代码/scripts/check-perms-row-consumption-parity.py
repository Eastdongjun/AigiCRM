#!/usr/bin/env python3
"""双端权限消费对齐契约: 后端行级 perms 必须被 PC/MP 显示层消费, 不允许页级判定顶替。

背景（2026-11-17 用户报障"销售总监不能改文件"）：后端 /file/list 每行返回 perms
（permsForFiles 与 checkFile 同源, 销售总监三空间全含 rename/replace, 运行态实测），
但线上 MP 1.0.69 的 buildFileRowView 按"页级夹 perms"判定——公共根目录页级 perms
被收敛为 ['view','download']（公共根仅管理员可上传的安全设计）→ 改名/分享/删除按钮
全部不出现。修复=42b9ac2e（T2-MP 行级 row.perms 优先消费），已含于 1.0.70+，
但 1.0.70~1.0.72 上传后未提审，线上仍为旧版——显示层漏消费后端数据源的同类问题
历史上已发生 4 次（激励分享制失效/ctxObjectTypeOf 词表错位/PC 单夹口缺词/本例），
故立此契约机器防复发。

判定（对真实文件, 只读）：
  C1 PC 行级优先: aigicrm-ui/src/views/file/FileTable.vue 存在
     Array.isArray(row?.perms) ? row.perms : <回退> 模式
  C2 MP 行级优先: aigicrm-miniprogram/pages/files/index.js 的 buildFileRowView 内
     存在 const actionPerms = rowPerms || folderPerms 形态(rowPerms 须先定义)
  C3 MP 写动作判定必须走行级(actionPerms/rowPerms): rename/delete/share 的
     includes 判定不允许直接引用 folderPerms(旧逻辑形态)
  C4 词表对齐: 后端 FolderPermissionService.FILE_ROW_ACTIONS 的每个动作词,
     MP buildFileRowView 内必须有消费点或落入内置豁免表(附用户令来源):
       view   → 点行=打开, 无需按钮
       replace→ 2026-10-26 用户令"三个点不需要替换"(操作面板移除, 详情页与 API 保留)
     后端新增动作词而 MP 无消费无豁免 → FAIL(防未来新增动作再漏端)。

负例自证：--self-test 在临时目录合成"旧逻辑"(folderPerms 直判, 无行级优先)必须检出,
合成"新逻辑"必须零违规。
"""
from __future__ import annotations
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MP_FILE = ROOT / 'aigicrm-miniprogram' / 'pages' / 'files' / 'index.js'
PC_FILE = ROOT / 'aigicrm-ui' / 'src' / 'views' / 'file' / 'FileTable.vue'
BACKEND_FILE = ROOT / 'aigicrm-backend' / 'src' / 'main' / 'java' / 'com' / 'aigicrm' / 'module' / 'file' / 'service' / 'FolderPermissionService.java'

# 内置豁免: 动作词 → (豁免理由, 来源)。豁免词必须有理由, 防豁免滥用。
ACTION_EXEMPT = {
    'view': ('点行=打开, 无需独立按钮', '产品形态 2026-10~11'),
    'replace': ('2026-10-26 用户令"三个点不需要替换"(操作面板移除, 详情页与后端 API 保留)', '用户令 2026-10-26'),
}


def read(p: Path) -> str:
    try:
        return p.read_text(encoding='utf-8')
    except OSError as e:
        print(f'FAIL: 无法读取 {p}: {e}', file=sys.stderr)
        sys.exit(2)


def extract_fn(text: str, name: str) -> str:
    """按括号配平提取 JS 函数体(从 function <name> 起至配平右括号)。"""
    m = re.search(rf'function {name}\s*\(', text)
    if not m:
        return ''
    i = text.index('{', m.start())
    depth = 0
    for j in range(i, len(text)):
        if text[j] == '{':
            depth += 1
        elif text[j] == '}':
            depth -= 1
            if depth == 0:
                return text[i:j + 1]
    return ''


def parse_backend_actions() -> set[str]:
    """解析 FILE_ROW_ACTIONS = List.of(P_VIEW, ...) 的动作词集(P_XXX 常量名→小写词)。"""
    text = read(BACKEND_FILE)
    m = re.search(r'FILE_ROW_ACTIONS\s*=\s*List\.of\(([^)]*)\)', text)
    if not m:
        print('FAIL: 后端 FILE_ROW_ACTIONS 定义未找到(结构变更?)', file=sys.stderr)
        sys.exit(2)
    words = set()
    for tok in m.group(1).split(','):
        tok = tok.strip()
        rm = re.fullmatch(r'P_([A-Z_]+)', tok)
        if rm:
            words.add(rm.group(1).lower())
    return words


def check(mp_text: str, pc_text: str, backend_actions: set[str], label: str = '真实文件') -> list[str]:
    """返回违规列表。对真实文件与合成负例共用同一断言(负例自证同源)。"""
    issues: list[str] = []

    # C1 PC 行级优先
    if not re.search(r'Array\.isArray\(row\?\.perms\)\s*\?\s*row\.perms\s*:', pc_text):
        issues.append(f'[{label}] C1 PC FileTable 缺行级优先模式(row?.perms ? row.perms : 回退)')

    # C2 MP 行级优先(rowPerms 定义 + actionPerms 优先回退)
    if 'const rowPerms = Array.isArray(item.perms)' not in mp_text:
        issues.append(f'[{label}] C2 MP buildFileRowView 缺 rowPerms 行级定义(item.perms 数组判定)')
    if not re.search(r'const\s+actionPerms\s*=\s*rowPerms\s*\|\|\s*folderPerms', mp_text):
        issues.append(f'[{label}] C2 MP 缺 actionPerms = rowPerms || folderPerms 行级优先回退')

    # C3 写动作判定禁止直判 folderPerms(旧逻辑形态=本次事故根因)
    fn_body = extract_fn(mp_text, 'buildFileRowView') or mp_text
    for line_no_base, line in enumerate(fn_body.splitlines(), 1):
        if re.search(r"folderPerms\.includes\(\s*'(rename|replace|delete|share|edit)'\s*\)", line):
            issues.append(f'[{label}] C3 写动作直判 folderPerms(旧逻辑形态): {line.strip()[:120]}')

    # C4 词表对齐: 后端动作词须在 MP 函数体内有消费点('word' 引号形式)或内置豁免
    for word in sorted(backend_actions):
        if word in ACTION_EXEMPT:
            continue
        if f"'{word}'" not in fn_body and f'"{word}"' not in fn_body:
            reason = ACTION_EXEMPT.get(word)
            issues.append(
                f'[{label}] C4 后端行级动作词 {word!r} 在 MP buildFileRowView 无消费点也无豁免'
                f'(新增动作漏端? 豁免须在 ACTION_EXEMPT 附用户令来源)')
    return issues


def main() -> int:
    if '--self-test' in sys.argv:
        return self_test()
    issues = check(read(MP_FILE), read(PC_FILE), parse_backend_actions())
    if issues:
        print('FAIL: 双端权限消费对齐契约违例:', file=sys.stderr)
        for i in issues:
            print(f'  {i}', file=sys.stderr)
        print('处置: 行级动作判定必须消费后端 row.perms(permsForFiles 与 checkFile 同源);', file=sys.stderr)
        print('      豁免须在 ACTION_EXEMPT 表附用户令来源; 参见 docs/生产部署规范.md 同类事故记录。', file=sys.stderr)
        return 1
    print('PASS: 双端权限消费对齐(PC/MP 行级优先 + 词表对齐 + 无页级直判写动作)')
    return 0


def self_test() -> int:
    """负例自证: 合成旧逻辑必须检出(C2/C3/C4), 合成新逻辑必须零违规。"""
    backend_actions = {'view', 'download', 'rename', 'replace', 'share', 'delete'}
    pc_ok = 'const src = Array.isArray(row?.perms) ? row.perms : folderPerms.value'
    # 旧逻辑(本次事故形态): 无 rowPerms, 写动作直判 folderPerms
    mp_old = (
        'function buildFileRowView(item, data) {\n'
        "  const folderPerms = Array.isArray(data.folderPerms) ? data.folderPerms : []\n"
        "  const showDelete = Boolean(data.canDelete && folderPerms.includes('delete'))\n"
        "  const showShare = Boolean(data.canShare && folderPerms.includes('share'))\n"
        "  if (data.canEditFile && folderPerms.includes('rename')) sheetActions.push({ key: 'rename' })\n"
        '}\n'
    )
    # 新逻辑(T2-MP 形态): 行级优先 + 动作词消费 + 豁免词不在
    mp_new = (
        'function buildFileRowView(item, data) {\n'
        '  const rowPerms = Array.isArray(item.perms) ? item.perms : null\n'
        '  const actionPerms = rowPerms || folderPerms\n'
        "  const showDownload = rowPerms ? (item.canDownload !== false && rowPerms.includes('download') || isOwn) : false\n"
        "  const showDelete = Boolean(data.canDelete && (actionPerms.includes('delete') || isOwn))\n"
        "  const showShare = Boolean(data.canShare && (actionPerms.includes('share') || isOwn))\n"
        "  if (data.canEditFile && (actionPerms.includes('edit') || actionPerms.includes('rename') || isOwn)) sheetActions.push({ key: 'rename' })\n"
        '}\n'
    )
    old_issues = check(mp_old, '/* 无行级模式 */', backend_actions, '负例-旧逻辑')
    need_old = [i for i in old_issues if ('C2' in i or 'C3' in i)]
    if len(need_old) < 3:  # C2 两条 + C3 至少一条
        print(f'FAIL: 负例自证未检出旧逻辑(检出 {len(need_old)} 条 C2/C3, 期望>=3)', file=sys.stderr)
        for i in old_issues:
            print(f'  {i}', file=sys.stderr)
        return 1
    new_issues = check(mp_new, pc_ok, backend_actions, '负例-新逻辑')
    if new_issues:
        print('FAIL: 负例自证对新逻辑误报:', file=sys.stderr)
        for i in new_issues:
            print(f'  {i}', file=sys.stderr)
        return 1
    print('PASS: 负例自证(旧逻辑检出>=3, 新逻辑零误报)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
