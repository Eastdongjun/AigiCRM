#!/usr/bin/env python3
# 权限码字典 markdown 生成(由 gen-permission-dict.sh 调用)
import sys, datetime

def guard_repo_path(path, what):
    """生成器只允许写项目目录内的路径, 拒绝越仓写入(路径穿越收敛, 2026-09-21 安全轮)。"""
    import os
    repo_root = os.path.realpath(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    p = os.path.realpath(path)
    if not (p == repo_root or p.startswith(repo_root + os.sep)):
        sys.exit('拒绝: %s 路径必须在项目目录内(%s): %s' % (what, repo_root, p))
    return p

def main(out_path, label):
    out_path = guard_repo_path(out_path, '输出文件')
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
    L.append('## 一、权限码清单(%d 条；启用 %d / 停用 %d)' % (len(grouped), enabled_cnt, disabled_cnt))
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
        L.append('| `%s` | %s | %s | `%s` | %s | %s |' % (
            perm,
            ' / '.join(sorted(g['names'])),
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
    for perm in sorted(grouped):
        cells = []
        for r in role_set:
            cells.append('✅' if r in roles.get(perm, []) else ' ')
        L.append('| `%s` | %s |' % (perm, ' | '.join(cells)))
    L.append('')

    with open(out_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(L) + '\n')
    print('生成完成: %s (%d 条权限码, 启用 %d / 停用 %d, %d 个角色)' % (
        out_path, len(grouped), enabled_cnt, disabled_cnt, len(role_set)))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
