#!/usr/bin/env python3
# 权限码字典 markdown 生成(由 gen-permission-dict.sh 调用)
import sys, datetime

def main(out_path, label):
    menus = []
    for line in open('/tmp/perm-menus.txt', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 5:
            menus.append({'id': p[0], 'name': p[1], 'perm': p[2], 'type': p[3], 'path': p[4]})

    roles = {}
    for line in open('/tmp/perm-roles.txt', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 2:
            roles.setdefault(p[1], set()).add(p[0])

    # 按权限码分组: 合并同名菜单, 角色去重
    grouped = {}
    for m in menus:
        g = grouped.setdefault(m['perm'], {'names': set(), 'types': set(), 'paths': set()})
        g['names'].add(m['name'])
        g['types'].add(m['type'])
        g['paths'].add(m['path'])

    L = []
    L.append('# AIGICRM 权限码字典(自动生成)')
    L.append('')
    L.append('> 生成时间: %s | 来源: %s sys_menu + sys_role_menu' % (datetime.date.today(), label))
    L.append('> 本文件由 scripts/gen-permission-dict.sh 自动生成,**禁止手改**;权限变更后重新运行。')
    L.append('> 格式约定: 权限码 = `模块:动作`(如 customer:view / system:user:add)')
    L.append('')
    L.append('## 一、权限码清单(%d 条)' % len(grouped))
    L.append('')
    L.append('| 权限码 | 菜单名 | 类型 | 路径 | 绑定的角色 |')
    L.append('|---|---|---|---|---|')
    for perm in sorted(grouped):
        g = grouped[perm]
        r = ','.join(sorted(roles.get(perm, []))) or '—'
        L.append('| `%s` | %s | %s | `%s` | %s |' % (
            perm,
            ' / '.join(sorted(g['names'])),
            ' / '.join(sorted(g['types'])),
            ' / '.join(sorted(g['paths'])),
            r))
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
    print('生成完成: %s (%d 条权限码, %d 个角色)' % (out_path, len(grouped), len(role_set)))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
