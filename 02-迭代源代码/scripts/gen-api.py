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
    method_mapping_re = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\((?:value\s*=\s*)?"([^"]*)"\))?')
    auth_re = re.compile(r'@PreAuthorize\("([^"]+)"\)')
    for root, dirs, files in os.walk(controller_dir):
        for f in sorted(files):
            if not f.endswith('Controller.java'):
                continue
            path = os.path.join(root, f)
            src = open(path, encoding='utf-8').read()
            class_match = re.search(r'@RequestMapping\("([^"]+)"\)', src)
            cls_path = class_match.group(1) if class_match else ''
            annotation_lines = []
            for line in src.splitlines():
                stripped = line.strip()
                if stripped.startswith('@'):
                    annotation_lines.append(stripped)
                    continue
                sig = re.match(r'public\s+.+\s+(\w+)\s*\(', stripped)
                if sig:
                    annotations = '\n'.join(annotation_lines)
                    mm = method_mapping_re.search(annotations)
                    if not mm:
                        annotation_lines = []
                        continue
                    method = mm.group(1).upper()
                    sub = mm.group(2) or ''
                    auth = auth_re.search(annotations)
                    perm = auth.group(1) if auth else ''
                    func = sig.group(1)
                else:
                    if stripped and not stripped.startswith('//') and not stripped.startswith('*') and not stripped.startswith('/*'):
                        annotation_lines = []
                    continue
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
                annotation_lines = []
    return apis

def main(ctrl_dir, out_path):
    apis = extract_apis(ctrl_dir)
    apis.sort(key=lambda a: (a['path'], a['method']))
    L = []
    L.append('# AIGICRM API 清单(自动生成)')
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
