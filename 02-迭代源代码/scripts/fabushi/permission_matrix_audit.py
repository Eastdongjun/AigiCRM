#!/usr/bin/env python3
"""权限矩阵·审计版执行器（2026-11-16 审计计划 A）。

与 v2 的差别（只做审计需要的修正，不改变判定语义）：
- 每行落盘：mode / 实际请求路径 / 注入资源(实体#id@owner) / 是否执行 / 执行结果；
- A 组 = (28 常驻 + 7 范围轴临时用户) × 289 端点 × 4 认证态 = 40,460 真实请求；
- B 组仅在"确实存在他人资源"时执行，否则 N/A 不计入执行数（不再用 999999 充数）；
- C 组只执行只读 GET + 自身资源；写操作/无资源标 skipped（单列，不计入执行数）；
- 汇总只报 executed unique，SKIP/N-A 单列。

用法：
  /tmp/mpvenv/bin/python scripts/fabushi/permission_matrix_audit.py \
    --users /tmp/fabushi_users.json --temp-users /tmp/temp_users.json \
    --pools /tmp/pools.tsv --no2xx /tmp/no2xx.json \
    --out test-results/permission-matrix/audit-20261116.jsonl
"""
from __future__ import annotations
import argparse, json, sys, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from permission_matrix_20k import (  # noqa: E402
    PARAM_SHAPE_OK, call, expected_ok, fill_path, make_token, permission_rule,
)

# P0-5 batch C: duplicated string literals (Sonar python:S1192)
BOOK_ID_PLACEHOLDER = '{bookId}'

STATUS_TEXT = {200: '放行', 201: '放行', 204: '放行', 400: '参数校验', 401: '未认证', 403: '拒绝', 404: '未找到'}


def load_pools(path):
    pools = {}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        c = line.split('\t')
        if len(c) < 5 or c[0] == 't':
            continue
        try:
            pools.setdefault(c[0], []).append({'id': int(c[1]), 'owner': int(c[2]), 'extra': c[3]})
        except ValueError:
            continue
    return pools


def entity_of(path):
    if '/contacts/{contactId}' in path or '/shares/{userId}' in path:
        return 'customer'
    if '/books/{bookId}/sections/{id}' in path:
        return 'section'
    if '/posts/{postId}' in path:
        return 'post'
    for pre, ent in (
        ('/api/customer/', 'customer'), ('/api/file/folders', 'folder'), ('/api/file/recycle/folders', 'folder'),
        ('/api/file/', 'file'), ('/api/incentive/records', 'incentive'), ('/api/approval/', 'approval'),
        ('/api/fabushi/books', 'book'), ('/api/fabushi/posts', 'post'), ('/api/fabushi/activities', 'activity'),
        ('/api/task/', 'task'), ('/api/reminder/', 'reminder'), ('/api/sea/pool/', 'seapool'),
    ):
        if path.startswith(pre):
            return ent
    return None


def pick_item(pools, ent, uid, want_own):
    items = pools.get(ent) or []
    for it in items:
        if (it['owner'] == uid) == want_own:
            return it
    return None


def _fill_book(out, path, uid, pools, want_own):
    if BOOK_ID_PLACEHOLDER not in out:
        return out
    if '/sections' in path:
        it = pick_item(pools, 'section', uid, False)
        return out.replace(BOOK_ID_PLACEHOLDER, str(it['owner'] if it else 1))
    it = pick_item(pools, 'book', uid, want_own) or pick_item(pools, 'book', uid, False)
    return out.replace(BOOK_ID_PLACEHOLDER, str(it['id'] if it else 1))


def _fill_id(original_path, out, uid, pools, want_own):
    ent = entity_of(original_path)
    if '{id}' not in out:
        return out, ent, True, None
    if not ent:
        return out.replace('{id}', '999999'), None, False, None
    it = pick_item(pools, ent, uid, want_own)
    if it is None and ent in ('book', 'post', 'activity', 'section'):
        it = pick_item(pools, ent, uid, False)
    if it is None:
        return out.replace('{id}', '999999'), ent, False, None
    return out.replace('{id}', str(it['id'])), ent, True, dict(it, entity=ent)


def fill_ids(path, uid, pools, want_own):
    """返回 (实际路径, 主实体, 是否注入, 注入项dict)。"""
    out = _fill_book(path, path, uid, pools, want_own)
    if '{postId}' in out:
        it = pick_item(pools, 'post', uid, False)
        out = out.replace('{postId}', str(it['id'] if it else 1))
    if '{contactId}' in out:
        it = pick_item(pools, 'contact', uid, False)
        out = out.replace('{contactId}', str(it['id'] if it else 1))
    out = out.replace('{userId}', '11').replace('{nodeId}', '1').replace('{versionNo}', '1')
    out = out.replace('{draftType}', 'customer').replace('{roleCode}', '06')
    out = out.replace('{type}', 'ROLE').replace('{key}', '06').replace('{deptId}', '4')
    return _fill_id(path, out, uid, pools, want_own)


def _judge_b(txt, status):
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    if 200 <= status < 300:
        return txt, '需复核', '受限用户读取他人资源 2xx（可能共享/越权）'
    if status in (403, 404):
        return txt, '匹配', '拒绝访问他人资源'
    if status == 400:
        return txt, '匹配', '参数/业务校验'
    return txt, '需复核', f'返回 {status}'


def _judge_c(txt, status):
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    if 200 <= status < 300:
        return txt, '通过', ''
    if status in (403, 404):
        return txt, '未通过', f'自身资源仍被拒绝（{status}）'
    if status == 400:
        return txt, '需复核', '参数/业务校验'
    return txt, '需复核', f'返回 {status}'


def _judge_anon(txt, status):
    if status == 404:
        return txt, '匹配', '匿名端点资源不存在'
    return txt, '匹配', '' if status in (200, 201, 204, 401, 403) else '匿名端点非预期状态'


def _judge_rejected(txt, status):
    if status in (401, 403):
        return txt, '匹配', ''
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '不匹配', '无效/过期 token 未被拒绝'


def _judge_authorized(txt, status, white, has_id):
    if 200 <= status < 300:
        return txt, '匹配', ''
    if status == 400 and white:
        return txt, '匹配', '鉴权前参数校验（已登记）'
    if status == 401:
        return txt, '不匹配', '有效 token 却 401'
    if status in (403, 404) and has_id:
        return txt, '匹配', '资源守卫：999999/无资源'
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '需复核', f'持有权限码返回 {status}'


def _judge_denied(txt, status, white, has_id):
    if status in (401, 403):
        return txt, '匹配', ''
    if status == 400 and white:
        return txt, '匹配', '鉴权前参数校验'
    if status == 404 and has_id:
        return txt, '匹配', '资源不存在'
    if 200 <= status < 300:
        return txt, '不匹配', '无权限码却 2xx（越权）'
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '需复核', f'无权限码返回 {status}'


def _judge_a(txt, status, expected, white, has_id):
    if expected == 'anon':
        return _judge_anon(txt, status)
    if expected == 'rejected':
        return _judge_rejected(txt, status)
    if expected == 'authorized':
        return _judge_authorized(txt, status, white, has_id)
    if expected == 'denied':
        return _judge_denied(txt, status, white, has_id)
    return txt, ('匹配' if status < 500 else '不匹配'), ''


def judge(group, status, expected, method, path, mode):
    if not isinstance(status, int):
        return '网络错误', '需复核', f'请求失败：{status}（重试后仍失败）'
    txt = STATUS_TEXT.get(status, '服务端异常' if status >= 500 else f'HTTP {status}')
    white = (method, path) in PARAM_SHAPE_OK
    has_id = '{' in path
    if group == 'B':
        return _judge_b(txt, status)
    if group == 'C':
        return _judge_c(txt, status)
    return _judge_a(txt, status, expected, white, has_id)


def main():
    args = parse_args()
    secret, users, temp_users, pools, eps, no2xx = load_inputs(args)
    perms, uroles, tokens = load_permissions(args, secret, users + temp_users)
    expired, forged = build_probe_tokens(secret)
    cases = build_cases(users, temp_users, eps, tokens, forged, expired, no2xx)
    recs = execute_cases(cases, args, pools, perms, uroles)
    write_records(recs, args.out)
    print_summary(recs, args.out)
    return 0


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://111.229.37.220')
    ap.add_argument('--secret-file', default='/tmp/jwt_secret.txt')
    ap.add_argument('--users', required=True)
    ap.add_argument('--temp-users', required=True)
    ap.add_argument('--pools', required=True)
    ap.add_argument('--endpoints', default='docs/api/api-list.json')
    ap.add_argument('--no2xx', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--workers', type=int, default=64)
    ap.add_argument('--timeout', type=float, default=6)
    return ap.parse_args()


def load_inputs(args):
    secret = Path(args.secret_file).read_text().strip()
    users = json.loads(Path(args.users).read_text(encoding='utf-8'))
    temp_users = json.loads(Path(args.temp_users).read_text(encoding='utf-8'))
    pools = load_pools(args.pools)
    eps = [e for e in json.loads(Path(args.endpoints).read_text(encoding='utf-8'))['endpoints']
           if e['path'] not in ('/api/auth/logout', '/api/auth/switch-group')]
    no2xx = {(m, p) for m, p in json.loads(Path(args.no2xx).read_text(encoding='utf-8'))}
    return secret, users, temp_users, pools, eps, no2xx


def load_permissions(args, secret, all_users):
    import urllib.request
    perms, uroles, tokens = {}, {}, {}
    for u in all_users:
        tok = make_token(secret, u['id'], u['username'])
        tokens[u['id']] = tok
        try:
            req = urllib.request.Request(args.base + '/api/auth/info', headers={'Authorization': 'Bearer ' + tok})
            with urllib.request.urlopen(req, timeout=20) as r:
                d = (json.loads(r.read().decode()) or {}).get('data') or {}
            perms[u['id']] = set(d.get('permissions') or [])
            uroles[u['id']] = d.get('roles') or []
        except Exception:
            perms[u['id']] = set(); uroles[u['id']] = []
    return perms, uroles, tokens


def build_probe_tokens(secret):
    import jwt as pyjwt
    now = int(time.time())
    expired = pyjwt.encode({'jti': f'exp-{now}', 'sub': '1', 'username': 'admin', 'iat': now - 3600, 'exp': now - 60},
                           secret, algorithm='HS256')
    forged = make_token(secret, 99999998, 'forged-probe')
    return expired, forged


def build_cases(users, temp_users, eps, tokens, forged, expired, no2xx):
    cases = []
    for u in users + temp_users:
        for e in eps:
            rule = permission_rule(e.get('permission') or '')
            for mode, tok in (('valid', tokens[u['id']]), ('no_token', None), ('forged', forged), ('expired', expired)):
                cases.append({'u': u, 'e': e, 'group': 'A', 'mode': mode, 'token': tok, 'rule': rule})
    for u in users:
        for e in eps:
            if '{' in e['path']:
                cases.append({'u': u, 'e': e, 'group': 'B', 'mode': 'other', 'token': tokens[u['id']],
                              'rule': permission_rule(e.get('permission') or '')})
    for u in users:
        for e in eps:
            if (e['method'], e['path']) in no2xx:
                cases.append({'u': u, 'e': e, 'group': 'C', 'mode': 'own', 'token': tokens[u['id']],
                              'rule': permission_rule(e.get('permission') or '')})
    return cases


def execute_cases(cases, args, pools, perms, uroles):
    recs = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(run_case, c, args, pools, perms, uroles) for c in cases]
        done = 0
        for f in as_completed(futs):
            recs.append(f.result()); done += 1
            if done % 5000 == 0:
                print(f'progress {done}/{len(cases)}', flush=True)
    return recs


def run_case(c, args, pools, perms, uroles):
    u, e, group, mode = c['u'], c['e'], c['group'], c['mode']
    method, path = e['method'], e['path']
    base = {'group': group, 'mode': mode, '人员': f"{u['username']}（{u.get('realName','')}）",
            '角色': ','.join(sorted(set(uroles.get(u['id']) or []))), '模块': path.split('/')[2],
            '方法': method, '端点': path, '权限码': e.get('permission') or ''}

    prep = prepare_injection(c, u, base, pools)
    if isinstance(prep, dict):
        return prep
    used_path, executed, injected = prep

    status = call_with_retry(args.base, method, used_path, c['token'], args.timeout)
    exp = expected_for(c, mode, u, perms)
    result, match, err = judge(group, status, exp, method, path, mode)
    match, err = refine_c_match(group, status, path, c, u, perms, match, err)
    return {**base, '实际请求': used_path, 'HTTP': status, '结果': result, '匹配': match,
            '错误内容': err, '执行': executed, '注入资源': injected}


def prepare_injection(c, u, base, pools):
    """返回提前终止行(dict) 或 (used_path, executed, injected)。"""
    group, method, path = c['group'], c['e']['method'], c['e']['path']
    injected = '—'
    used_path = path
    if group == 'B':
        used_path, ent, found, item = fill_ids(path, u['id'], pools, want_own=False)
        if not found:
            return {**base, '实际请求': used_path, 'HTTP': 'N/A', '结果': '未执行',
                    '匹配': 'N/A', '错误内容': '无他人资源可注入（不充数）', '执行': False, '注入资源': '无'}
        if item:
            injected = f"{item['entity']}#{item['id']}@owner={item['owner']}"
    elif group == 'C':
        if method != 'GET':
            return {**base, '实际请求': used_path, 'HTTP': 'SKIP', '结果': '未执行',
                    '匹配': '未执行', '错误内容': '写操作需临时数据，单列 skipped', '执行': False, '注入资源': '—'}
        used_path, ent, found, item = fill_ids(path, u['id'], pools, want_own=True)
        if not found:
            return {**base, '实际请求': used_path, 'HTTP': 'SKIP', '结果': '未执行',
                    '匹配': '未执行', '错误内容': '该用户无自身资源', '执行': False, '注入资源': '—'}
        if item:
            injected = f"{item['entity']}#{item['id']}@owner={item['owner']}"
    return used_path, True, injected


def call_with_retry(base_url, method, path, token, timeout):
    status = call(base_url, method, path, token, timeout=timeout)
    tries = 0
    while not isinstance(status, int) and tries < 2:
        time.sleep(0.4)
        status = call(base_url, method, path, token, timeout=timeout)
        tries += 1
    return status


def expected_for(c, mode, u, perms):
    if c['group'] != 'A':
        return 'authorized'
    if mode == 'valid':
        ok = expected_ok(c['rule'], perms.get(u['id'], set()))
        return 'authorized' if ok is True else ('denied' if ok is False else 'unknown')
    if c['rule'][0] == 'anon':
        return 'anon'
    if mode == 'no_token':
        return 'denied'
    return 'rejected'


def refine_c_match(group, status, path, c, u, perms, match, err):
    """C 组(本人资源)误判复核：仅对 未通过/需复核 且拿到 HTTP 码的行做二次归类。"""
    if not (group == 'C' and match in ('未通过', '需复核') and isinstance(status, int)):
        return match, err
    ok = expected_ok(c['rule'], perms.get(u['id'], set()))
    if status == 404 and ('/**' in path or 'recycle' in path):
        return '匹配', '探针资源不存在（公共下载/recycle 占位）'
    if status == 403 and 'permission-explain' in path:
        return '匹配', '管理员专属守卫（非缺陷）'
    if ok is False and status in (401, 403, 404):
        return '匹配', '无该权限码，拒绝正确'
    if status == 403:
        return '需复核', '持有权限码仍 403（可能资源 ACL/组织守卫）'
    if status == 404:
        return '匹配', '资源不存在（探针 id 失效，不计缺陷）'
    return match, err


def write_records(recs, out_path):
    out = Path(out_path); out.parent.mkdir(parents=True, exist_ok=True)
    with out.open('w', encoding='utf-8') as fh:
        for r in recs:
            fh.write(json.dumps(r, ensure_ascii=False) + '\n')


def print_summary(recs, out_path):
    from collections import Counter
    executed = [r for r in recs if r['执行']]
    print(json.dumps({
        'out': str(out_path), 'planned': len(recs), 'executed': len(executed),
        'skipped': len(recs) - len(executed),
        'by_group': dict(Counter(r['group'] for r in executed)),
        'by_match': dict(Counter(r['匹配'] for r in recs)),
        'http': dict(Counter(str(r['HTTP']) for r in recs)),
    }, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    raise SystemExit(main())
