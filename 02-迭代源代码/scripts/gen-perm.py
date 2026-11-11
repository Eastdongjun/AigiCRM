#!/usr/bin/env python3
# 权限码字典 markdown 生成(由 gen-permission-dict.sh 调用)
import os, sys, datetime, tempfile

# 兼容/历史权限码(与 PermissionMatrixTest.DOCUMENTED_NON_RUNTIME_PERMISSION_CODES 同源)。
# 这些码已从 sys_menu 删除或不再作为运行时门禁, 但测试要求字典"一、权限码清单"里可查,
# 且行内需带 不作为/已废弃/历史残留/统一使用/授权表 之一的口径说明。
LEGACY_PERMISSION_CODES = [
    ('customer:share', '历史兼容项, 不作为独立运行时授权(沿用客户访问/编辑守卫)'),
    ('incentive:rank:export', '前端导出门码, 后端不作为端点守卫(用户裁决 2026-11-05 独立导出码, 前端 CSV 导出, 与消费侧契约白名单同口径)'),
    ('incentive:person-rank:export', '前端导出门码, 后端不作为端点守卫(用户裁决 2026-09-23 独立导出码, 前端 CSV 导出, 与消费侧契约白名单同口径)'),
    ('incentive:delete', '历史兼容项, 不作为独立运行时授权(由授权表与服务范围控制)'),
    ('incentive:view-all', '历史残留, 不作为运行时授权(改由授权表按人核定)'),
    ('incentive:view-shared', '历史残留, 不作为运行时授权(改由授权表按人核定)'),
    ('score:ranking', '历史残留, 不作为独立运行时授权(统一使用 score:view)'),
    ('sea:pool:delete', '历史残留, 不作为独立运行时授权(运行时由池级 PERM_DELETE 守卫控制)'),
    ('statistics:scoreboard', '历史残留, 不作为独立运行时授权(统一使用 statistics:view)'),
    ('system:menu:edit', '历史残留, 不作为运行时授权(菜单由迁移维护)'),
]
LEGACY_NOTES = dict(LEGACY_PERMISSION_CODES)

def _allowed_roots(allow_tempdir=False):
    repo_root = os.path.realpath(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    roots = [repo_root]
    if allow_tempdir:
        roots.append(os.path.realpath(tempfile.gettempdir()))
        roots.append(os.path.realpath('/tmp'))
    return roots

def guard_repo_path(path, what, allow_tempdir=False):
    """生成器只允许写项目目录(或系统临时目录, 供 check-doc-freshness 临时产物用)内的路径。
    先 abspath 归一化 ..，再 realpath 解析符号链接，最后做根前缀校验；输出父目录在
    校验通过后才创建，创建后再次 realpath 复核，拒绝越仓/绝对路径逃逸(2026-10-17 加固)。"""
    if not path or '\x00' in path:
        sys.exit('拒绝: %s 路径为空或包含 NUL 字节' % what)
    raw = os.path.abspath(path)
    p = os.path.realpath(raw)
    roots = _allowed_roots(allow_tempdir)
    allowed = any(p == r or p.startswith(r + os.sep) for r in roots)
    if not allowed:
        sys.exit('拒绝: %s 路径必须在项目目录(或系统临时目录)内(%s): %s' % (what, roots[0], p))
    parent = os.path.dirname(p) or '.'
    try:
        os.makedirs(parent, exist_ok=True)
    except OSError as exc:
        sys.exit('拒绝: %s 父目录无法创建(%s): %s' % (what, exc, parent))
    p = os.path.realpath(raw)
    if not any(p == r or p.startswith(r + os.sep) for r in roots):
        sys.exit('拒绝: %s 路径解析后逃逸允许根(%s): %s' % (what, roots[0], p))
    return p

def write_text(path, text, what='输出文件'):
    """落盘前紧邻调用 guard_repo_path，避免"校验一次后另行 open"的路径穿越/TOCTOU。"""
    p = guard_repo_path(path, what, allow_tempdir=True)
    if os.path.isdir(p):
        sys.exit('拒绝: %s 指向目录而非文件: %s' % (what, p))
    with open(p, 'w', encoding='utf-8') as f:
        f.write(text)

def main(out_path, label):
    out_path = guard_repo_path(out_path, '输出文件', allow_tempdir=True)
    menus = []
    for line in open('/tmp/perm-menus.txt', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 5:
            # 第 6 列为 status（1=启用 / 0=停用）。兼容旧格式：缺列时按 None 处理。
            status = p[5] if len(p) >= 6 else None
            menus.append({'id': p[0], 'name': p[1], 'perm': p[2], 'type': p[3],
                          'path': p[4], 'status': status})

    roles = {}
    for line in open('/tmp/perm-roles.txt', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 2:
            roles.setdefault(p[1], set()).add(p[0])

    # 按权限码分组: 合并同名菜单, 角色去重
    # 同时收集 status: 一个码可能对应多行菜单，只要任一行 status=1 即视为"可授予"。
    grouped = {}
    for m in menus:
        g = grouped.setdefault(m['perm'], {'names': set(), 'types': set(), 'paths': set(),
                                           'enabled': False, 'has_status': False})
        g['names'].add(m['name'])
        g['types'].add(m['type'])
        g['paths'].add(m['path'])
        if m['status'] is not None:
            g['has_status'] = True
            if m['status'].strip() == '1':
                g['enabled'] = True

    enabled_cnt = sum(1 for g in grouped.values() if g['enabled'])
    disabled_cnt = sum(1 for g in grouped.values() if g['has_status'] and not g['enabled'])
    db_cnt = len(grouped)
    db_perms = set(grouped)  # 角色矩阵只覆盖库内真实存在的码(兼容码无绑定, 列矩阵无意义)
    # 已从 sys_menu 消失的兼容码补成表行(状态 '—'), 与库内码同表呈现, 供架构测试与角色配置追溯
    for code, note in LEGACY_PERMISSION_CODES:
        if code not in grouped:
            grouped[code] = {'names': {note}, 'types': {'—'}, 'paths': {'—'},
                             'enabled': False, 'has_status': False}
    legacy_cnt = len(grouped) - db_cnt

    L = []
    L.append('# AIGICRM 权限码字典(自动生成)')
    L.append('')
    L.append('> 生成时间: %s | 来源: %s sys_menu + sys_role_menu' % (datetime.date.today(), label))
    L.append('> 本文件由 scripts/gen-permission-dict.sh 自动生成,**禁止手改**;权限变更后重新运行。')
    L.append('> 格式约定: 权限码 = `模块:动作`(如 customer:view / system:user:add)')
    L.append('>')
    L.append('> **状态列口径**: `启用` = sys_menu.status=1，该码会出现在角色管理页的配置树中'
             '(SysMenuService.tree() 带 status=1 过滤)，**管理员可勾选授予**;'
             ' `停用` = status=0，**管理员看不到、也无法授予**（即便数据库里存在绑定）。')
    L.append('')
    L.append('## 一、权限码清单(%d 条；数据库 %d 条 + 兼容/历史 %d 条；启用 %d / 停用 %d)'
             % (len(grouped), db_cnt, legacy_cnt, enabled_cnt, disabled_cnt))
    L.append('')
    L.append('| 权限码 | 菜单名 | 类型 | 路径 | 状态 | 绑定的角色 |')
    L.append('|---|---|---|---|---|---|')
    for perm in sorted(grouped):
        g = grouped[perm]
        r = ','.join(sorted(roles.get(perm, []))) or '—'
        if not g['has_status']:
            st = '—'
        else:
            st = '启用' if g['enabled'] else '**停用**'
        # 库内仍存在的停用/兼容码: 在菜单名后追注口径, 满足架构测试"非运行时说明"要求
        name_text = ' / '.join(sorted(g['names']))
        if perm in LEGACY_NOTES and '停用兼容码' not in name_text and g['has_status']:
            name_text = name_text + '（' + LEGACY_NOTES[perm] + '）'
        L.append('| `%s` | %s | %s | `%s` | %s | %s |' % (
            perm,
            name_text,
            ' / '.join(sorted(g['types'])),
            ' / '.join(sorted(g['paths'])),
            st,
            r))
    L.append('')
    L.append('> ⚠️ 表中「绑定的角色」取自 `sys_role_menu`，**不代表该码当前可授予**：'
             '`停用` 的码即使有绑定行，也不会进入任何用户的 permissions[]'
             '（`SysMenuMapper.selectPermissionsByUserId` 带 `AND m.status = 1`）。')
    L.append('')
    L.append('## 二、角色权限矩阵')
    L.append('')
    role_set = sorted(set(r for rs in roles.values() for r in rs))
    L.append('| 权限码 | ' + ' | '.join(role_set) + ' |')
    L.append('|---|' + '---|' * len(role_set))
    for perm in sorted(db_perms):
        cells = []
        for r in role_set:
            cells.append('✅' if r in roles.get(perm, []) else ' ')
        L.append('| `%s` | %s |' % (perm, ' | '.join(cells)))
    L.append('')

    write_text(out_path, '\n'.join(L) + '\n')
    print('生成完成: %s (%d 条权限码, 启用 %d / 停用 %d, %d 个角色)' % (
        out_path, len(grouped), enabled_cnt, disabled_cnt, len(role_set)))

if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('用法: gen-perm.py <输出md> <来源标签>')
    main(sys.argv[1], sys.argv[2])
