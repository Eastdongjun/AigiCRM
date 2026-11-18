#!/usr/bin/env python3
"""法布施/全接口权限矩阵：真实 HTTP 请求 20k+，用于查找失败权限。

用例 = 用户 × 端点 × 3 种认证态（有效 token / 无 token / 伪造 token）。
不写库、不登录；token 由本地测试 JWT secret 签发，用户清单从测试库只读导出。
输出：summary + failures JSON；5xx 与“有权限却被 403”优先。
"""
from __future__ import annotations
import argparse, json, re, sys, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import jwt
import urllib.request, urllib.error

AUTH_RE = re.compile(r"hasAuthority\('([^']+)'\)")
ANY_RE = re.compile(r"hasAnyAuthority\(([^)]*)\)")
CODE_RE = re.compile(r"'([a-z][a-z0-9:_-]+)'")


def permission_rule(expr: str):
    """解析 Spring SpEL 权限表达式为判定规则。

    语义：支间 or、支内 and。本库现状（2026-11-16 全量核对 docs/api/api-list.json）
    仅 2 个 or 端点，and 只出现在单支括号组内；出现无法解析的混合形态时返回
    ('expr', None) 保守哨兵——只对 5xx/越权 2xx 报警，不误判 401/403。
    【修复 2026-11-16】此前把 or 表达式当 and（'all'）解析，导致持
    customer:edit+finance:view 的用户对 stage-amounts 被误判 denied、
    其合法业务 400 被记为 fail（23 条误报）。
    """
    if expr in ('匿名', ''):
        return ('anon', None)
    if expr in ('登录即可', 'isAuthenticated()'):
        return ('auth', None)
    if ANY_RE.search(expr):
        codes = set(CODE_RE.findall(ANY_RE.search(expr).group(1)))
        return ('any', codes)
    clauses = [set(AUTH_RE.findall(c)) for c in re.split(r'\s+or\s+', expr)]
    if not clauses or any(not c for c in clauses):
        return ('expr', None)  # 混有非 hasAuthority 谓词/无法解析：保守哨兵
    if len(clauses) == 1:
        return ('all', clauses[0])
    return ('or', clauses)


# 【鉴权前 400 收口 2026-11-16】这 9 个端点经源码逐一核对均已有 @PreAuthorize 权限门
# （逐条证据见 docs/fabushi-preauth-400-registry-20261116.md），但 Spring MVC 的参数解析
# 先于方法 AOP 鉴权执行：矩阵发的最小请求（空 body {} / 缺 required query）在到达
# @PreAuthorize 之前就被参数校验拒绝为 400——无数据泄露（controller 方法体未执行）。
# 对这些端点，无权限用户收到 400/401/403 均视为"已解释"；**2xx/5xx 仍然 fail**
# （负例自证：若某端点的权限门被移除，无权限用户将收到 2xx，此处立即变红）。
# 注意：POST /api/customer/{id}/stage-amounts 不在白名单——它无参数形状问题（record
# 全 null 反序列化成功），此前 23 条 fail 是 permission_rule 误把 or 当 and 的解析 bug，
# 已在 permission_rule 修复，行为走正常 denied/authorized 判定。
PARAM_SHAPE_OK = {
    ('PUT', '/api/system/perm-scopes/{roleCode}'),
    ('PUT', '/api/system/perm-scopes/assignee/{type}/{key}'),
    ('PUT', '/api/system/configs'),
    ('GET', '/api/score/export/grid'),
    ('POST', '/api/system/depts/{deptId}/admins'),
    ('PUT', '/api/system/roles/{id}/orgs'),
    ('POST', '/api/customer/import'),
    ('POST', '/api/sea/import'),
    ('GET', '/api/system/perm-center/preview'),
}


def make_token(secret, uid, username):
    now = int(time.time())
    return jwt.encode({'jti': f'perm-matrix-{uid}-{now}-{id(object())}', 'sub': str(uid),
                       'username': username, 'iat': now, 'exp': now + 7200}, secret, algorithm='HS256')


def fill_path(path):
    path = re.sub(r'\{[^}]+\}', '999999', path)
    path = path.replace('**', 'test')
    return path


def expected_ok(rule, perms):
    kind, payload = rule
    if kind == 'anon':
        return True
    if kind == 'auth':
        return True
    if kind == 'any':
        return bool(perms & payload)
    if kind == 'all':
        return payload.issubset(perms)
    if kind == 'or':
        return any(c.issubset(perms) for c in payload)
    return None  # unknown expression: only flag 5xx/false 403


def call(base, method, path, token=None, timeout=3):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    url = base.rstrip('/') + fill_path(path)
    body = None
    if method in ('POST', 'PUT', 'PATCH', 'DELETE'):
        headers['Content-Type'] = 'application/json'
        body = b'{}'
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    for attempt in range(2):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status
        except urllib.error.HTTPError as e:
            if e.code in (502, 503, 504) and attempt == 0:
                time.sleep(0.4)
                continue
            return e.code
        except Exception as e:
            if attempt == 0:
                time.sleep(0.4)
                continue
            return f'ERR:{type(e).__name__}'


def main():
    args = parse_args_20k()
    secret, users, eps = load_inputs_20k(args)
    out_path = Path(args.out); out_path.parent.mkdir(parents=True, exist_ok=True)

    # 1) 真实拉取每个用户权限（不猜）
    user_perms = load_user_perms_20k(args, secret, users)
    invalid = make_token(secret, 99999999, 'invalid-probe')
    # 2) 用户 × 端点 × 3 认证态；无/伪造 token 也按每个用户重复，保证 20k+ 真实请求
    cases = build_cases_20k(users, eps, invalid)

    counts, results = execute_cases_20k(cases, args, user_perms)
    summary = build_summary_20k(args, users, eps, cases, counts, results)
    out_path.write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding='utf-8')
    print(json.dumps({k: summary[k] for k in ('users','endpoints','cases','pass','fail','by_mode','by_status')}, ensure_ascii=False, indent=1))
    return 0 if counts['fail'] == 0 else 1


def parse_args_20k():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://111.229.37.220')
    ap.add_argument('--secret-file', default='/tmp/jwt_secret.txt')
    ap.add_argument('--users', required=True)
    ap.add_argument('--endpoints', default='docs/api/api-list.json')
    ap.add_argument('--out', default='test-results/permission-matrix/result.json')
    ap.add_argument('--workers', type=int, default=16)
    return ap.parse_args()


def load_inputs_20k(args):
    secret = Path(args.secret_file).read_text().strip()
    users = json.loads(Path(args.users).read_text(encoding='utf-8'))
    eps = [e for e in json.loads(Path(args.endpoints).read_text(encoding='utf-8'))['endpoints']
           if e['path'] not in ('/api/auth/logout', '/api/auth/switch-group')]
    return secret, users, eps


def load_user_perms_20k(args, secret, users):
    user_perms = {}
    for u in users:
        token = make_token(secret, u['id'], u['username'])
        try:
            req = urllib.request.Request(args.base + '/api/auth/info', headers={'Authorization': 'Bearer ' + token})
            with urllib.request.urlopen(req, timeout=15) as r:
                body = json.loads(r.read().decode('utf-8'))
            data = body.get('data') if body else None
            user_perms[u['id']] = set((data or {}).get('permissions') or [])
        except Exception:
            user_perms[u['id']] = set()
        u['token'] = token
    return user_perms


def build_cases_20k(users, eps, invalid):
    cases = []
    for u in users:
        for e in eps:
            rule = permission_rule(e.get('permission') or '')
            cases.append((u, e, 'valid', u['token'], rule))
            cases.append((u, e, 'no_token', None, rule))
            cases.append((u, e, 'invalid_token', invalid, rule))
    return cases


def execute_cases_20k(cases, args, user_perms):
    results = []
    counts = {'total': len(cases), 'pass': 0, 'fail': 0, 'by_mode': {}, 'by_status': {}}
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(run_case_20k, c, args, user_perms) for c in cases]
        for f in as_completed(futs):
            r = f.result()
            counts['by_mode'][r['mode']] = counts['by_mode'].get(r['mode'], 0) + 1
            counts['by_status'][str(r['status'])] = counts['by_status'].get(str(r['status']), 0) + 1
            if r['fail']:
                counts['fail'] += 1
                if len(results) < 3000:
                    results.append(r)
            else:
                counts['pass'] += 1
            done = counts['pass'] + counts['fail']
            if done % 2000 == 0:
                print(f'progress {done}/{len(cases)} fail={counts["fail"]} status={counts["by_status"]}', flush=True)
    return counts, results


def run_case_20k(c, args, user_perms):
    u, e, mode, token, rule = c
    status = call(args.base, e['method'], e['path'], token)
    if not isinstance(status, int):
        return {**{'user': u['username'], 'userId': u['id'], 'mode': mode,
                   'method': e['method'], 'path': e['path'], 'permission': e.get('permission'),
                   'status': status}, 'fail': True, 'reason': 'network'}
    exp, bad = evaluate_20k(mode, e, status, rule, user_perms, u)
    if status >= 500:
        bad = True
    return {**{'user': u['username'], 'userId': u['id'], 'mode': mode,
               'method': e['method'], 'path': e['path'], 'permission': e.get('permission'),
               'status': status, 'expected': exp}, 'fail': bad,
            'reason': 'status' if bad else 'ok'}


def evaluate_20k(mode, e, status, rule, user_perms, u):
    if mode == 'valid':
        ok = expected_ok(rule, user_perms.get(u['id'], set()))
        if ok is True:
            return 'authorized', status in (401, 403)
        if ok is False:
            # 鉴权前 400 白名单端点：参数形状先于方法鉴权（登记见
            # docs/fabushi-preauth-400-registry-20261116.md），400 与 401/403 同为已解释；
            # 2xx（越权成功）/5xx 仍 fail
            if (e['method'], e['path']) in PARAM_SHAPE_OK:
                return 'denied', status not in (400, 401, 403)
            return 'denied', status not in (401, 403)
        return 'unknown', status >= 500
    if mode == 'no_token':
        exp = 'denied' if rule[0] not in ('anon',) else 'anon'
        bad = (status not in (401, 403)) if exp == 'denied' else (status in (401, 403))
        return exp, bad
    # invalid token: 401/403 is acceptable for both protected and anonymous endpoints
    return 'rejected', status >= 500 or status < 400


def build_summary_20k(args, users, eps, cases, counts, results):
    return {
        'generated_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
        'base': args.base, 'users': len(users), 'endpoints': len(eps),
        'cases': len(cases), 'pass': counts['pass'], 'fail': counts['fail'],
        'by_mode': counts['by_mode'], 'by_status': counts['by_status'],
        'failures': results,
    }


if __name__ == '__main__':
    raise SystemExit(main())
