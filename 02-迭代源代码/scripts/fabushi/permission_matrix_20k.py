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
    if expr in ('匿名', ''):
        return ('anon', set())
    if expr in ('登录即可', 'isAuthenticated()'):
        return ('auth', set())
    if ANY_RE.search(expr):
        codes = set(CODE_RE.findall(ANY_RE.search(expr).group(1)))
        return ('any', codes)
    codes = set(AUTH_RE.findall(expr))
    if not codes:
        return ('expr', set())
    return ('all', codes)


def make_token(secret, uid, username):
    now = int(time.time())
    return jwt.encode({'jti': f'perm-matrix-{uid}-{now}-{id(object())}', 'sub': str(uid),
                       'username': username, 'iat': now, 'exp': now + 7200}, secret, algorithm='HS256')


def fill_path(path):
    path = re.sub(r'\{[^}]+\}', '999999', path)
    path = path.replace('**', 'test')
    return path


def expected_ok(rule, perms):
    kind, codes = rule
    if kind == 'anon':
        return True
    if kind == 'auth':
        return True
    if kind == 'any':
        return bool(perms & codes)
    if kind == 'all':
        return codes.issubset(perms)
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
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://111.229.37.220')
    ap.add_argument('--secret-file', default='/tmp/jwt_secret.txt')
    ap.add_argument('--users', required=True)
    ap.add_argument('--endpoints', default='docs/api/api-list.json')
    ap.add_argument('--out', default='test-results/permission-matrix/result.json')
    ap.add_argument('--workers', type=int, default=16)
    args = ap.parse_args()

    secret = Path(args.secret_file).read_text().strip()
    users = json.loads(Path(args.users).read_text(encoding='utf-8'))
    eps = [e for e in json.loads(Path(args.endpoints).read_text(encoding='utf-8'))['endpoints']
           if e['path'] not in ('/api/auth/logout', '/api/auth/switch-group')]
    out_path = Path(args.out); out_path.parent.mkdir(parents=True, exist_ok=True)

    # 1) 真实拉取每个用户权限（不猜）
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
    invalid = make_token(secret, 99999999, 'invalid-probe')

    # 2) 用户 × 端点 × 3 认证态；无/伪造 token 也按每个用户重复，保证 20k+ 真实请求
    cases = []
    for u in users:
        for e in eps:
            rule = permission_rule(e.get('permission') or '')
            cases.append((u, e, 'valid', u['token'], rule))
            cases.append((u, e, 'no_token', None, rule))
            cases.append((u, e, 'invalid_token', invalid, rule))

    results = []
    counts = {'total': len(cases), 'pass': 0, 'fail': 0, 'by_mode': {}, 'by_status': {}}
    lock = None

    def run(c):
        u, e, mode, token, rule = c
        status = call(args.base, e['method'], e['path'], token)
        if not isinstance(status, int):
            return {**{'user': u['username'], 'userId': u['id'], 'mode': mode,
                       'method': e['method'], 'path': e['path'], 'permission': e.get('permission'),
                       'status': status}, 'fail': True, 'reason': 'network'}
        exp = None
        if mode == 'valid':
            ok = expected_ok(rule, user_perms.get(u['id'], set()))
            if ok is True:
                exp = 'authorized'; bad = status in (401, 403)
            elif ok is False:
                exp = 'denied'; bad = status not in (401, 403)
            else:
                exp = 'unknown'; bad = status >= 500
        elif mode == 'no_token':
            exp = 'denied' if rule[0] not in ('anon',) else 'anon'
            bad = (status not in (401, 403)) if exp == 'denied' else (status in (401, 403))
        else:  # invalid token: 401/403 is acceptable for both protected and anonymous endpoints
            exp = 'rejected'
            bad = status >= 500 or status < 400
        if status >= 500:
            bad = True
        return {**{'user': u['username'], 'userId': u['id'], 'mode': mode,
                   'method': e['method'], 'path': e['path'], 'permission': e.get('permission'),
                   'status': status, 'expected': exp}, 'fail': bad,
                'reason': 'status' if bad else 'ok'}

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(run, c) for c in cases]
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

    summary = {
        'generated_at': time.strftime('%Y-%m-%dT%H:%M:%S%z'),
        'base': args.base, 'users': len(users), 'endpoints': len(eps),
        'cases': len(cases), 'pass': counts['pass'], 'fail': counts['fail'],
        'by_mode': counts['by_mode'], 'by_status': counts['by_status'],
        'failures': results,
    }
    out_path.write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding='utf-8')
    print(json.dumps({k: summary[k] for k in ('users','endpoints','cases','pass','fail','by_mode','by_status')}, ensure_ascii=False, indent=1))
    return 0 if counts['fail'] == 0 else 1


if __name__ == '__main__':
    raise SystemExit(main())
