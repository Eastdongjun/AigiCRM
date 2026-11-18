#!/usr/bin/env python3
"""权限矩阵全量导出（逐例落盘，供 PC/MP 权限核查报表）。

与 permission_matrix_20k.py 的关系：判定口径、token 签发、路径填充、鉴权前 400 白名单
全部 import 复用（单一事实源）；本脚本只做三件事：
  1) 逐例落盘（JSONL），不只保留 failures —— 报表需要 用户×端点×认证态 全量；
  2) 补齐元数据：模块 / 动作项 / 用户角色 / 该角色在该模块动作上的配置数据范围；
  3) 给出四值判定：放行 / 拒绝 / 守卫拒绝 / 服务端异常，并标注“匹配 / 不匹配 / 需复核”。

用法（需 PyJWT 环境）：
  /tmp/mpvenv/bin/python scripts/fabushi/permission_matrix_full.py \
    --users /tmp/fabushi_users.json --meta /tmp/perm_meta.json \
    --out test-results/permission-matrix/full-20261116.jsonl
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from permission_matrix_20k import (  # noqa: E402  复用单源判定口径
    PARAM_SHAPE_OK,
    call,
    expected_ok,
    fill_path,
    make_token,
    permission_rule,
)

STATUS_TEXT = {
    200: '放行', 201: '放行', 204: '放行',
    400: '参数校验', 401: '未认证', 403: '拒绝', 404: '未找到',
}


def scope_of_user(meta, uid, module, action):
    """用户在该模块/动作上的配置数据范围（role_perm_scope，含 USER 级覆盖）。

    返回 (scope_level, anchor, 来源说明)。无配置行 → 角色默认 data_scope。
    """
    role_codes = [r['role_code'] for r in meta['roles'] if r['user_id'] == str(uid)]
    rows = [
        s for s in meta['scopes']
        if s['module'] in (module, '*') and s['action'] in (action, '*')
        and ((s['assignee_type'] == 'ROLE' and s['assignee_key'] in role_codes)
             or (s['assignee_type'] == 'USER' and s['assignee_key'] == str(uid)))
    ]
    if rows:
        levels = sorted({r['scope_level'] for r in rows})
        anchors = sorted({r['anchor'] for r in rows})
        return ','.join(levels), ','.join(anchors), 'role_perm_scope'
    defaults = [r['data_scope'] for r in meta['roles'] if r['user_id'] == str(uid)]
    return (','.join(sorted(set(defaults))) if defaults else '—'), '—', '角色默认'


def target_of_endpoint(meta, e, code):
    """端点 → (模块, 动作项)：优先权限码映射（与后端 SCOPE_TARGETS parity），回退路径段。"""
    m = meta['code_map'].get(code)
    if m:
        return m['module'], m['action']
    seg = e['path'].split('/')
    module = seg[2] if len(seg) > 2 else '—'
    return module, e['method'].lower()


def _classify_authorized(txt, status, in_white, has_id):
    if 200 <= status < 300:
        return txt, '匹配', ''
    if status == 400 and in_white:
        return txt, '匹配', '鉴权前参数校验（已登记，权限门未缺失）'
    if status == 401:
        return txt, '不匹配', '有效 token 却 401（疑似鉴权链异常）'
    if status in (403, 404) and has_id:
        return txt, '匹配', '资源守卫：探针 id=999999 不存在/不可见'
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '需复核', f'持有权限码但返回 {status}'


def _classify_denied(txt, status, in_white, has_id):
    if status in (401, 403):
        return txt, '匹配', ''
    if status == 400 and in_white:
        return txt, '匹配', '鉴权前参数校验（已登记）'
    if status == 404 and has_id:
        return txt, '匹配', '资源不存在（未泄露数据）'
    if 200 <= status < 300:
        return txt, '不匹配', '无权限码却 2xx（越权）'
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '需复核', f'无权限码返回 {status}'


def _classify_valid(txt, expected, status, in_white, has_id):
    if expected == 'authorized':
        return _classify_authorized(txt, status, in_white, has_id)
    if expected == 'denied':
        return _classify_denied(txt, status, in_white, has_id)
    # 无法解析的表达式：只对 5xx 报警
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '匹配', '表达式无法解析，仅 5xx 报警'


def _classify_no_token(txt, status, rule):
    if rule[0] == 'anon':
        return txt, ('匹配' if not (401 <= status < 500) else '需复核'), ''
    if status in (401, 403):
        return txt, '匹配', ''
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '不匹配', '无 token 未拒绝'


def _classify_invalid(txt, status):
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    if status >= 400:
        return txt, '匹配', ''
    return txt, '不匹配', '伪造 token 未被拒绝'


def classify(mode, expected, status, method, path, rule):
    """四值判定 + 匹配结论。返回 (结果, 匹配, 错误内容)。"""
    if not isinstance(status, int):
        return '网络错误', '需复核', f'请求失败：{status}（并发抖动/超时）'
    txt = STATUS_TEXT.get(status, '服务端异常' if status >= 500 else f'HTTP {status}')
    in_white = (method, path) in PARAM_SHAPE_OK
    has_id = '{' in path

    if mode == 'valid':
        return _classify_valid(txt, expected, status, in_white, has_id)
    if mode == 'no_token':
        return _classify_no_token(txt, status, rule)
    return _classify_invalid(txt, status)


def main():
    args = parse_args()
    secret, users, meta, eps = load_inputs(args)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)

    # 1) 真实拉取每个用户的权限码与角色（不猜）
    perms, roles = load_permissions(args, secret, users)
    invalid = make_token(secret, 99999999, 'invalid-probe')
    cases = build_cases(users, eps, invalid)

    counters = {'总用例': len(cases), '匹配': 0, '不匹配': 0, '需复核': 0}
    lock_result = []
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(run_case, c, args, perms, roles, meta) for c in cases]
        done = 0
        for f in as_completed(futs):
            rec = f.result()
            counters[rec['是否匹配']] = counters.get(rec['是否匹配'], 0) + 1
            lock_result.append(rec)
            done += 1
            if done % 2000 == 0:
                print(f"progress {done}/{len(cases)} match={counters.get('匹配', 0)} "
                      f"bad={counters.get('不匹配', 0)} review={counters.get('需复核', 0)} "
                      f"elapsed={int(time.time() - t0)}s", flush=True)

    with out.open('w', encoding='utf-8') as fh:
        for rec in lock_result:
            fh.write(json.dumps(rec, ensure_ascii=False) + '\n')
    print(json.dumps({'out': str(out), 'elapsed_s': int(time.time() - t0), **counters}, ensure_ascii=False, indent=1))
    return 0


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://111.229.37.220')
    ap.add_argument('--secret-file', default='/tmp/jwt_secret.txt')
    ap.add_argument('--users', required=True)
    ap.add_argument('--meta', required=True)
    ap.add_argument('--endpoints', default='docs/api/api-list.json')
    ap.add_argument('--out', required=True)
    ap.add_argument('--workers', type=int, default=64)
    ap.add_argument('--timeout', type=float, default=6)
    return ap.parse_args()


def load_inputs(args):
    secret = Path(args.secret_file).read_text().strip()
    users = json.loads(Path(args.users).read_text(encoding='utf-8'))
    meta = json.loads(Path(args.meta).read_text(encoding='utf-8'))
    eps = [e for e in json.loads(Path(args.endpoints).read_text(encoding='utf-8'))['endpoints']
           if e['path'] not in ('/api/auth/logout', '/api/auth/switch-group')]
    return secret, users, meta, eps


def load_permissions(args, secret, users):
    perms, roles = {}, {}
    for u in users:
        tok = make_token(secret, u['id'], u['username'])
        u['token'] = tok
        try:
            import urllib.request
            req = urllib.request.Request(args.base + '/api/auth/info', headers={'Authorization': 'Bearer ' + tok})
            with urllib.request.urlopen(req, timeout=20) as r:
                data = (json.loads(r.read().decode('utf-8')) or {}).get('data') or {}
            perms[u['id']] = set(data.get('permissions') or [])
            roles[u['id']] = data.get('roles') or []
        except Exception:
            perms[u['id']] = set()
            roles[u['id']] = []
    return perms, roles


def build_cases(users, eps, invalid):
    cases = []
    for u in users:
        for e in eps:
            rule = permission_rule(e.get('permission') or '')
            for mode, tok in (('valid', u['token']), ('no_token', None), ('invalid_token', invalid)):
                cases.append((u, e, mode, tok, rule))
    return cases


def run_case(c, args, perms, roles, meta):
    u, e, mode, tok, rule = c
    status = call(args.base, e['method'], e['path'], tok, timeout=args.timeout)
    exp = expected_for(mode, u, perms, rule)

    code = (rule[1] and next(iter(rule[1]))) if rule[0] in ('all', 'any') else None
    module, action = target_of_endpoint(meta, e, code)
    scope, anchor, scope_src = scope_of_user(meta, u['id'], module, action)
    result, match, err = classify(mode, exp, status, e['method'], e['path'], rule)
    return {
        '模块': module, '动作项': action,
        '数据范围(配置)': f'{scope} / 锚点 {anchor}（{scope_src}）',
        '人员': f"{u['username']}（{u.get('realName', '')}）",
        '角色': ','.join(sorted(set(roles.get(u['id']) or []))) or '—',
        '认证态': {'valid': '有效token', 'no_token': '无token', 'invalid_token': '伪造token'}[mode],
        '方法': e['method'], '端点': e['path'], '权限码': e.get('permission') or '',
        'HTTP': status, '生效结果': result, '预期': exp or '',
        '是否匹配': match, '是否有错误': '否' if match == '匹配' else ('是' if match == '不匹配' else '待复核'),
        '错误内容': err,
    }


def expected_for(mode, u, perms, rule):
    if mode == 'valid':
        ok = expected_ok(rule, perms.get(u['id'], set()))
        return 'authorized' if ok is True else ('denied' if ok is False else 'unknown')
    if mode == 'no_token':
        return 'anon' if rule[0] == 'anon' else 'denied'
    return 'rejected'


if __name__ == '__main__':
    raise SystemExit(main())
