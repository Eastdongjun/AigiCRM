#!/usr/bin/env python3
# API 清单生成: 扫描 Spring controller 源码, 提取接口路径/方法/权限码
# 用法: gen-api.py <controller根目录> <输出md>
import os, re, sys, datetime

METHOD_MAP = {
    'GetMapping': 'GET', 'PostMapping': 'POST', 'PutMapping': 'PUT',
    'DeleteMapping': 'DELETE', 'PatchMapping': 'PATCH', 'RequestMapping': 'ANY',
}

def extract_apis(controller_dir):
    apis = []
    for root, dirs, files in os.walk(controller_dir):
        for f in sorted(files):
            if not f.endswith('Controller.java'):
                continue
            path = os.path.join(root, f)
            src = open(path, encoding='utf-8').read()
            # 类级路径 + 类级权限
            cls_path = re.search(r'@RequestMapping\("([^"]+)"\)', src)
            cls_path = cls_path.group(1) if cls_path else ''
            cls_perm = ''
            m = re.search(r'@PreAuthorize\("hasAuthority\(\'([^\']+)\'\)"\)\s*\n\s*public class', src)
            if not m:
                m = re.search(r'class\s+\w+Controller', src)
            # 按方法块切分: 上一个方法签名之后 到 本方法签名之前 = 本方法的注解区
            sig_pat = re.compile(r'public\s+[\w<>,\[\] ]+\s+(\w+)\s*\(', re.S)
            sigs = [(m.start(), m.group(1)) for m in sig_pat.finditer(src)]
            for idx, (pos, func) in enumerate(sigs):
                block_start = sigs[idx - 1][0] if idx > 0 else 0
                block = src[block_start:pos]
                mm = re.search(
                    r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)'
                    r'\s*(?:\(\s*"([^"]*)"\s*\))?', block)
                if not mm:
                    continue
                method = METHOD_MAP[mm.group(1)]
                sub = mm.group(2) or ''
                perm = ''
                pm = re.search(
                    r"""@PreAuthorize\("has(?:Any)?Authority\('([^']+)'(?:,'[^']+')*\)"\)""", block)
                if pm:
                    perm = pm.group(1)
                # 绝对路径(以 /api 开头)不拼类级路径
                if sub.startswith('/api'):
                    full = sub
                else:
                    full = (cls_path + sub).replace('//', '/')
                # 权限标注: 无方法级权限码的, 区分"匿名"与"登录即可"
                if not perm:
                    if any(k in full for k in ('/auth/login', '/auth/captcha', '/auth/forgot-password',
                                               '/configs/public', '/configs/logo', '/file/download/public')):
                        perm = '匿名'
                    else:
                        perm = '登录即可'
                apis.append({'method': method, 'path': full, 'perm': perm, 'func': func, 'file': f})
    return apis

def main(ctrl_dir, out_path):
    apis = extract_apis(ctrl_dir)
    apis.sort(key=lambda a: (a['path'], a['method']))
    L = []
    L.append('# YCMN API 清单(自动生成)')
    L.append('')
    L.append('> 生成时间: %s | 来源: 后端 controller 源码扫描' % datetime.date.today())
    L.append('> 本文件由 scripts/gen-api.py 自动生成,**禁止手改**;接口变更后重新运行。')
    L.append('> 权限码与 docs/permission/permission-dict.md 对应。')
    L.append('')
    L.append('## 接口清单(%d 个)' % len(apis))
    L.append('')
    L.append('| 方法 | 路径 | 权限码 | 方法名 | 文件 |')
    L.append('|---|---|---|---|---|')
    for a in apis:
        L.append('| %s | `%s` | `%s` | %s | %s |' % (a['method'], a['path'], a['perm'] or '—(匿名)', a['func'], a['file']))
    L.append('')
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(L) + '\n')
    print('生成完成: %s (%d 个接口)' % (out_path, len(apis)))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
