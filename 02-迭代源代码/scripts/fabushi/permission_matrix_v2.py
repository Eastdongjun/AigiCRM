#!/usr/bin/env python3
"""权限矩阵 v2 执行器：A 四认证态 / B 他人资源越权 / C 自身资源正面 / D 全权限角色范围轴。

- A：28 人 × 289 端点 × 4 态（有效/无/伪造/过期）= 32,368
- B：145 个含路径参数端点 × 28 人 × 他人真实资源 id = 4,060
- C：145 个“无 2xx”端点 × 28 人 × 自身真实资源（只读类执行，写类标 SKIP）= 4,060
- D：7 个全权限临时用户（all/self/dept/office/region/team/custom）× 289 端点 = 2,023
合计 42,511。

判定口径复用 permission_matrix_20k（单源）；本脚本只增加分组/资源注入/结果归类。
"""
from __future__ import annotations
import argparse, json, sys, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from permission_matrix_20k import (  # noqa: E402
    PARAM_SHAPE_OK, call, expected_ok, fill_path, make_token, permission_rule,
)

STATUS_TEXT = {200: '放行', 201: '放行', 204: '放行', 400: '参数校验', 401: '未认证', 403: '拒绝', 404: '未找到'}

ENT_OWNER_COL = {'customer': 2, 'file': 2, 'incentive': 2, 'approval': 2, 'reminder': 2, 'task': 2,
                 'book': 2, 'folder': 2, 'contact': 2, 'invoice': 3, 'payment': 3, 'section': 2,
                 'post': 2, 'activity': 2, 'score': 2, 'draft': 2, 'seapool': 2}


def load_pools(path):
    pools = {}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        parts = line.split('\t')
        if len(parts) < 5 or parts[0] == 't':
            continue
        ent = parts[0]
        try:
            pools.setdefault(ent, []).append({'id': int(parts[1]), 'owner': int(parts[2]), 'extra': parts[3]})
        except ValueError:
            continue
    return pools


ENTITY_PREFIXES = [
    ('/api/customer/{id}/contacts/{contactId}', 'contact'),
    ('/api/customer/{id}/shares/{userId}', 'customer'),
    ('/api/customer/', 'customer'),
    ('/api/file/folders', 'folder'),
    ('/api/file/recycle/folders', 'folder'),
    ('/api/file/', 'file'),
    ('/api/incentive/records', 'incentive'),
    ('/api/approval/', 'approval'),
    ('/api/fabushi/books/{bookId}/sections', 'section'),
    ('/api/fabushi/books', 'book'),
    ('/api/fabushi/posts/{postId}', 'post'),
    ('/api/fabushi/posts', 'post'),
    ('/api/fabushi/activities', 'activity'),
    ('/api/task/', 'task'),
    ('/api/reminder/', 'reminder'),
    ('/api/sea/pool/', 'seapool'),
]


def entity_of(path):
    for prefix, entity in ENTITY_PREFIXES:
        if path.startswith(prefix):
            return entity
    return None


def pick(pools, ent, uid, want_own):
    items = pools.get(ent) or []
    if not items:
        return None
    for it in items:
        own = (it['owner'] == uid)
        if own == want_own:
            return it['id']
    return items[0]['id'] if not want_own else None


def id_entity(path):
    """路径中泛化 {id} 对应的实体（具名参数另行处理）。"""
    if '/contacts/{contactId}' in path or '/shares/{userId}' in path:
        return 'customer'
    if '/books/{bookId}/sections/{id}' in path:
        return 'section'
    if '/posts/{postId}' in path:
        return 'post'
    return entity_of(path)


def fill_ids(path, uid, pools, want_own, all_scope=False):
    """把路径参数替换为真实资源 id。返回 (path, 使用的实体, 是否找到)。"""
    out = path
    if '{bookId}' in out:
        out = out.replace('{bookId}', str(pick(pools, 'book', uid, False) or 1))
    if '{postId}' in out:
        out = out.replace('{postId}', str(pick(pools, 'post', uid, False) or 1))
    if '{contactId}' in out:
        out = out.replace('{contactId}', str(pick(pools, 'contact', uid, False) or 1))
    if '{userId}' in out:
        out = out.replace('{userId}', '11')
    out = out.replace('{nodeId}', '1').replace('{versionNo}', '1')
    out = out.replace('{draftType}', 'customer').replace('{roleCode}', '06')
    out = out.replace('{type}', 'ROLE').replace('{key}', '06').replace('{deptId}', '4')
    ent = id_entity(path)
    if '{id}' not in out:
        return out, ent, True
    if not ent:
        return out.replace('{id}', '999999'), None, True
    rid = pick(pools, ent, uid, want_own)
    if rid is None:
        return out.replace('{id}', '999999'), ent, False
    return out.replace('{id}', str(rid)), ent, True


def _judge_a_anon(txt, status):
    # 匿名端点：无效/过期 token 被 401/403 拒绝属可接受（鉴权链先于 permitAll 亦可）
    return txt, '匹配', '匿名端点（无效 token 被拒绝亦可接受）' if status in (401, 403) else ''


def _judge_a_rejected(txt, status):
    if status in (401, 403):
        return txt, '匹配', ''
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '不匹配', '无效/过期 token 未被拒绝'


def _judge_a_authorized(txt, status, in_white, path):
    if 200 <= status < 300:
        return txt, '匹配', ''
    if status == 400 and in_white:
        return txt, '匹配', '鉴权前参数校验（已登记）'
    if status == 401:
        return txt, '不匹配', '有效 token 却 401'
    if status in (403, 404) and '{' in path:
        return txt, '匹配', '资源守卫：探针 999999 不存在'
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '需复核', f'持有权限码返回 {status}'


def _judge_a_denied(txt, status, in_white, path):
    if status in (401, 403):
        return txt, '匹配', ''
    if status == 400 and in_white:
        return txt, '匹配', '鉴权前参数校验'
    if status == 404 and '{' in path:
        return txt, '匹配', '资源不存在'
    if 200 <= status < 300:
        return txt, '不匹配', '无权限码却 2xx（越权）'
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    return txt, '需复核', f'无权限码返回 {status}'


def _judge_a(txt, status, expected, in_white, path):
    if expected in ('rejected', 'anon'):
        if expected == 'anon':
            return _judge_a_anon(txt, status)
        return _judge_a_rejected(txt, status)
    if expected == 'authorized':
        return _judge_a_authorized(txt, status, in_white, path)
    if expected == 'denied':
        return _judge_a_denied(txt, status, in_white, path)
    return None


def _judge_b(txt, status, all_scope):
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    if 200 <= status < 300:
        if all_scope:
            return txt, '匹配', '全量范围，读取他人资源属预期'
        return txt, '需复核', '受限范围读取他人资源 2xx（可能越权/共享授权）'
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
        return txt, '未通过', f'自身资源仍被拒绝（{status}），可能权限/资源守卫'
    if status == 400:
        return txt, '需复核', '参数/业务校验未通过，需有效 payload'
    return txt, '需复核', f'返回 {status}'


def _judge_d(txt, status, in_white, path):
    if status >= 500:
        return txt, '不匹配', f'服务端 {status}'
    if 200 <= status < 300:
        return txt, '匹配', ''
    if status == 400 and in_white:
        return txt, '匹配', '鉴权前参数校验'
    if status in (403, 404) and '{' in path:
        return txt, '匹配', '资源守卫（999999）'
    if status in (400, 403, 404):
        return txt, '需复核', f'全权限角色返回 {status}'
    return txt, '需复核', f'返回 {status}'


def judge(group, status, expected, method, path, scope_level):
    all_scope = (scope_level == 'all')
    if not isinstance(status, int):
        return '网络错误', '需复核', f'请求失败：{status}'
    txt = STATUS_TEXT.get(status, '服务端异常' if status >= 500 else f'HTTP {status}')
    in_white = (method, path) in PARAM_SHAPE_OK

    if group == 'A':
        result = _judge_a(txt, status, expected, in_white, path)
        if result is not None:
            return result
    if group == 'B':
        return _judge_b(txt, status, all_scope)
    if group == 'C':
        return _judge_c(txt, status)
    return _judge_d(txt, status, in_white, path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://111.229.37.220')
    ap.add_argument('--secret-file', default='/tmp/jwt_secret.txt')
    ap.add_argument('--users', required=True)
    ap.add_argument('--temp-users', default='/tmp/temp_users.json')
    ap.add_argument('--pools', default='/tmp/pools.tsv')
    ap.add_argument('--endpoints', default=str(ROOT / 'docs/api/api-list.json'))
    ap.add_argument('--no2xx', default='/tmp/no2xx.json')
    ap.add_argument('--out', required=True)
    ap.add_argument('--workers', type=int, default=64)
    ap.add_argument('--timeout', type=float, default=6)
    args = ap.parse_args()

    secret = Path(args.secret_file).read_text().strip()
    users = json.loads(Path(args.users).read_text(encoding='utf-8'))
    temp_users = json.loads(Path(args.temp_users).read_text(encoding='utf-8'))
    pools = load_pools(args.pools)
    eps = [e for e in json.loads(Path(args.endpoints).read_text(encoding='utf-8'))['endpoints']
           if e['path'] not in ('/api/auth/logout', '/api/auth/switch-group')]
    no2xx = {(m, p) for m, p in json.loads(Path(args.no2xx).read_text(encoding='utf-8'))}

    # 权限与角色
    perms, uroles = {}, {}
    def fetch_info(u):
        tok = make_token(secret, u['id'], u['username'])
        try:
            import urllib.request
            req = urllib.request.Request(args.base + '/api/auth/info', headers={'Authorization': 'Bearer ' + tok})
            with urllib.request.urlopen(req, timeout=20) as r:
                d = (json.loads(r.read().decode()) or {}).get('data') or {}
            perms[u['id']] = set(d.get('permissions') or [])
            uroles[u['id']] = d.get('roles') or []
        except Exception:
            perms[u['id']] = set(); uroles[u['id']] = []
        return tok
    tokens = {}
    for u in users + temp_users:
        tokens[u['id']] = fetch_info(u)
    now = int(time.time())
    import jwt as pyjwt
    expired = pyjwt.encode({'jti': f'expired-{now}', 'sub': '1', 'username': 'admin', 'iat': now - 3600, 'exp': now - 60},
                           secret, algorithm='HS256')
    forged = make_token(secret, 99999998, 'forged-probe')

    cases = []
    def add(u, e, group, mode, token, extra=None):
        rule = permission_rule(e.get('permission') or '')
        cases.append({'u': u, 'e': e, 'group': group, 'mode': mode, 'token': token,
                      'rule': rule, 'extra': extra or {}})

    for u in users:
        for e in eps:
            add(u, e, 'A', 'valid', tokens[u['id']])
            add(u, e, 'A', 'no_token', None)
            add(u, e, 'A', 'forged', forged)
            add(u, e, 'A', 'expired', expired)
    for u in users:
        for e in eps:
            if '{' in e['path']:
                add(u, e, 'B', 'other', tokens[u['id']])
    for u in users:
        for e in eps:
            if (e['method'], e['path']) in no2xx:
                add(u, e, 'C', 'own', tokens[u['id']])
    for u in temp_users:
        for e in eps:
            add(u, e, 'D', 'valid', tokens[u['id']])

    def scope_level_of(u):
        roles = uroles.get(u['id']) or []
        return 'all' if ('admin' in roles or '0' in roles or 'perm_tmp_all' in roles) else 'limited'

    def run(c):
        u, e, group, mode = c['u'], c['e'], c['group'], c['mode']
        method, path = e['method'], e['path']
        tok = c['token']
        used_path = path
        meta = ''
        if group == 'B':
            used_path, ent, found = fill_ids(path, u['id'], pools, want_own=False, all_scope=False)
            meta = f'他人资源({ent or "无"})'
        elif group == 'C':
            if method != 'GET':
                return {'group': group, '人员': f"{u['username']}（{u.get('realName','')}）", '角色': ','.join(sorted(set(uroles.get(u['id']) or []))),
                        '方法': method, '端点': path, '权限码': e.get('permission') or '', 'HTTP': 'SKIP',
                        '结果': '未执行', '匹配': '未执行', '错误内容': '写操作正面用例需临时数据，本轮跳过（只读优先）', '模块': path.split('/')[2], '动作项': method.lower()}
            used_path, ent, found = fill_ids(path, u['id'], pools, want_own=True, all_scope=False)
            if not found:
                return {'group': group, '人员': f"{u['username']}（{u.get('realName','')}）", '角色': ','.join(sorted(set(uroles.get(u['id']) or []))),
                        '方法': method, '端点': path, '权限码': e.get('permission') or '', 'HTTP': 'SKIP',
                        '结果': '未执行', '匹配': '未执行', '错误内容': '该用户无自身资源，未执行', '模块': path.split('/')[2], '动作项': method.lower()}
            meta = f'自身资源({ent})'

        status = call(args.base, method, used_path, tok, timeout=args.timeout)

        if group == 'A':
            if mode == 'valid':
                ok = expected_ok(c['rule'], perms.get(u['id'], set()))
                exp = 'authorized' if ok is True else ('denied' if ok is False else 'unknown')
            elif c['rule'][0] == 'anon':
                exp = 'anon'
            elif mode == 'no_token':
                exp = 'denied'
            else:  # forged / expired
                exp = 'rejected'
        elif group == 'D':
            exp = 'authorized'
            ok = expected_ok(c['rule'], perms.get(u['id'], set()))
            exp = 'authorized' if ok is True else ('denied' if ok is False else 'unknown')
        else:
            exp = 'authorized'

        result, match, err = judge(group, status, exp, method, path, scope_level_of(u))
        if group == 'C' and match == '未通过':
            ok = expected_ok(c['rule'], perms.get(u['id'], set()))
            if ok is False and status in (401, 403, 404):
                match, err = '匹配', '无该权限码，拒绝正确'
            elif status == 404 and '/sections' in path:
                match, err = '需复核', '探针构造：section 与 book 不匹配（非权限问题）'
        if group == 'B' and match == '需复核':
            if not meta or meta.endswith('(无)'):
                match, err = '匹配', '非资源参数（无他人资源可注入）'
            elif 'fabushi' in path:
                match, err = '匹配', '法布施社区内容对全员可见（预期）'
        return {'group': group, '人员': f"{u['username']}（{u.get('realName','')}）",
                '角色': ','.join(sorted(set(uroles.get(u['id']) or []))),
                '数据范围': meta or '', '方法': method, '端点': path,
                '权限码': e.get('permission') or '', 'HTTP': status,
                '结果': result, '匹配': match, '错误内容': err,
                '模块': path.split('/')[2] if len(path.split('/')) > 2 else '—',
                '动作项': method.lower()}

    out = Path(args.out); out.parent.mkdir(parents=True, exist_ok=True)
    recs = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(run, c) for c in cases]
        done = 0
        for f in as_completed(futs):
            recs.append(f.result()); done += 1
            if done % 5000 == 0:
                print(f'progress {done}/{len(cases)}', flush=True)
    with out.open('w', encoding='utf-8') as fh:
        for r in recs:
            fh.write(json.dumps(r, ensure_ascii=False) + '\n')
    from collections import Counter
    print(json.dumps({'out': str(out), 'cases': len(recs),
                      'by_group': Counter(r['group'] for r in recs),
                      'by_match': Counter(r['匹配'] for r in recs)}, ensure_ascii=False, indent=1, default=dict))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
