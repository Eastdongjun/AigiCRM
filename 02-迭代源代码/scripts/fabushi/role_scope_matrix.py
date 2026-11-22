#!/usr/bin/env python3
"""B1 角色范围矩阵：用 DB 配置计算期望，对照 /api/auth/info 与客户详情实际结果。

输入：/tmp/b1_config.tsv（USER/DEPT/ROLE/UR/PERM/RPS/UORG/CUST 分段）
输出：JSON 报告 + 控制台摘要。
用法：
  python3 scripts/fabushi/role_scope_matrix.py --base http://111.229.37.220 \
    --secret-file /tmp/jwt_secret.txt --config /tmp/b1_config.tsv \
    --out test-results/role-scope/role-scope-20261121.json
"""
from __future__ import annotations
import argparse, json, time, urllib.request, urllib.error, jwt
from collections import defaultdict


def parse_config(path, extra=None):
    users, depts, roles, user_roles, perms, rps, uorg, customers, shares = {}, {}, {}, defaultdict(set), defaultdict(set), [], defaultdict(list), [], set()
    for line in open(path, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if not p: continue
        k = p[0]
        try:
            if k == 'USER':
                uid = int(p[1]); users[uid] = {'id': uid, 'username': p[2], 'dept': int(p[3]) if p[3] not in ('', 'NULL') else None,
                    'office': int(p[4]) if p[4] not in ('', 'NULL') else None, 'manager': int(p[5]) if p[5] not in ('', 'NULL') else None,
                    'override': p[6] if p[6] not in ('NULL',) else ''}
            elif k == 'DEPT':
                depts[int(p[1])] = {'id': int(p[1]), 'parent': int(p[2]) if p[2] not in ('', 'NULL') else 0,
                    'type': int(p[3]) if p[3] not in ('', 'NULL') else None, 'path': p[4]}
            elif k == 'ROLE':
                roles[int(p[1])] = {'id': int(p[1]), 'code': p[2], 'scope': p[3]}
            elif k == 'UR':
                user_roles[int(p[1])].add(int(p[2]))
            elif k == 'PERM':
                perms[int(p[1])].add(p[2])
            elif k == 'RPS':
                rps.append({'assignee_type': p[1], 'assignee_key': p[2], 'module': p[3], 'action': p[4],
                            'scope': p[5], 'anchor': p[6]})
            elif k == 'UORG':
                uorg[int(p[1])].append((int(p[2]), int(p[3])))
            elif k == 'CUST':
                customers.append({'id': int(p[1]), 'owner': int(p[2]) if p[2] not in ('', 'NULL') else None,
                                  'dept': int(p[3]) if p[3] not in ('', 'NULL') else None,
                                  'office': int(p[4]) if p[4] not in ('', 'NULL') else None,
                                  'name': p[6] if len(p) > 6 else ''})
        except (ValueError, IndexError):
            continue
    if extra:
        for line in open(extra, encoding='utf-8'):
            p = line.rstrip('\n').split('\t')
            if not p: continue
            if p[0] == 'CUST' and len(p) >= 7:
                try:
                    cid = int(p[1]); pool = p[7] if len(p) > 7 else None
                    if pool in ('NULL', '', 'None'): pool = None
                    for c in customers:
                        if c['id'] == cid: c['pool'] = pool
                except ValueError: pass
            elif p[0] == 'SHARE' and len(p) >= 3:
                try: shares.add((int(p[1]), int(p[2])))
                except ValueError: pass
    return users, depts, roles, user_roles, perms, rps, uorg, customers, shares


RANK = {'all': 0, 'custom': 1, 'region': 2, 'office': 3, 'dept': 4, 'team': 7, 'self': 8}


def widest(scopes):
    scopes = [s for s in scopes if s]
    return min(scopes, key=lambda s: RANK.get(s, 99)) if scopes else 'self'


def rps_for(role_code, module, action, rps):
    rows = [r for r in rps if r['assignee_type'] == 'ROLE' and r['assignee_key'] == str(role_code)]
    exact = [r for r in rows if r['module'] == module and r['action'] == action]
    if exact: return exact[0]
    mod = [r for r in rows if r['module'] == module and r['action'] == '*']
    if mod: return mod[0]
    act = [r for r in rows if r['module'] == '*' and r['action'] == action]
    if act: return act[0]
    star = [r for r in rows if r['module'] == '*' and r['action'] == '*']
    return star[0] if star else None


class ScopeModel:
    def __init__(self, users, depts, roles, user_roles, rps, uorg):
        self.users, self.depts, self.roles, self.user_roles, self.rps, self.uorg = users, depts, roles, user_roles, rps, uorg
        self.children = defaultdict(list)
        for d in depts.values(): self.children[d['parent']].append(d['id'])
        self.subs = defaultdict(set)
        for uid in users:
            m = users[uid]['manager']; depth = 0
            while m and m in users and depth < 5:
                self.subs[m].add(uid); m = users[m]['manager']; depth += 1

    def subtree(self, root):
        out, stack = set(), [root]
        while stack:
            x = stack.pop()
            if x in out: continue
            out.add(x); stack.extend(self.children.get(x, []))
        return out

    def ancestor_type(self, dept_id, dtype):
        cur = dept_id
        for _ in range(20):
            if cur not in self.depts: return None
            if self.depts[cur]['type'] == dtype: return cur
            cur = self.depts[cur]['parent']
        return None

    def visible(self, uid, module, action):
        """返回 (scope, anchor, dept_ids set or None, user_ids set or None)."""
        role_ids = self.user_roles.get(uid, set())
        scopes, anchors = [], []
        for rid in role_ids:
            role = self.roles.get(rid)
            if not role: continue
            slot = rps_for(role['code'], module, action, self.rps)
            scopes.append(slot['scope'] if slot else role['scope'])
            anchors.append(slot['anchor'] if slot else 'OWNER')
        u = self.users[uid]
        if u['override']: scopes.append(u['override'])
        scope = widest(scopes)
        anchor = anchors[0] if anchors else 'OWNER'
        # 任一角色 all 直接全量
        if 'all' in scopes: return 'all', anchor, None, None

        dept_ids, user_ids = set(), set()
        for s in set(scopes):
            if s == 'self':
                user_ids.add(uid); dept_ids.add(u['dept'])
            elif s == 'team':
                user_ids.add(uid); user_ids |= self.subs.get(uid, set())
                for x in user_ids:
                    if x in self.users and self.users[x]['dept']: dept_ids.add(self.users[x]['dept'])
                if u['dept']: dept_ids.add(u['dept'])
            elif s == 'dept':
                d = u['dept']; sub = self.subtree(d) if d else set()
                dept_ids |= sub
                user_ids |= {x for x, v in self.users.items() if v['dept'] in sub}
            elif s == 'office':
                d = self.ancestor_type(u['dept'], 3) if u['dept'] else None
                sub = self.subtree(d) if d else set()
                dept_ids |= sub
                user_ids |= {x for x, v in self.users.items() if v['dept'] in sub}
            elif s == 'region':
                d = self.ancestor_type(u['dept'], 2) if u['dept'] else None
                sub = self.subtree(d) if d else set()
                dept_ids |= sub
                user_ids |= {x for x, v in self.users.items() if v['dept'] in sub}
            elif s == 'custom':
                for org, inc in self.uorg.get(uid, []):
                    sub = self.subtree(org) if inc else {org}
                    dept_ids |= sub
                    user_ids |= {x for x, v in self.users.items() if v['dept'] in sub}
        return scope, anchor, dept_ids, user_ids

    def expected_customer(self, uid, cust, perms=None, shares=None, include_pool=False):
        perms = perms or set(); shares = shares or set()
        if cust.get('owner') == uid or (cust['id'], uid) in shares: return True, 'owner_or_share', 'OWNER'
        if include_pool and cust.get('pool') and 'sea:view' in perms: return True, 'sea_pool', 'POOL'
        scope, anchor, dept_ids, user_ids = self.visible(uid, 'customer', 'view')
        if scope == 'all': return True, 'all', anchor
        if anchor == 'ORG':
            ok = cust['dept'] in dept_ids if cust['dept'] else False
        else:
            ok = cust['owner'] in user_ids if cust['owner'] else False
        return ok, scope, anchor


def make_token(secret, uid, username):
    n = int(time.time())
    return jwt.encode({'jti': f'b1-{uid}-{n}', 'sub': str(uid), 'username': username, 'iat': n, 'exp': n + 1800},
                      secret, algorithm='HS256')


def http_get(base, path, token):
    req = urllib.request.Request(base + path, headers={'Authorization': 'Bearer ' + token})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            b = r.read()
            return r.status, json.loads(b) if b else None
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception as e:
        return 'ERR', str(e)[:120]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default='http://111.229.37.220')
    ap.add_argument('--secret-file', default='/tmp/jwt_secret.txt')
    ap.add_argument('--config', default='/tmp/b1_config.tsv')
    ap.add_argument('--out', default='test-results/role-scope/role-scope-20261121.json')
    ap.add_argument('--extra', default='/tmp/b1_extra.tsv')
    ap.add_argument('--max-customers', type=int, default=60)
    ap.add_argument('--include-pool', action='store_true')
    args = ap.parse_args()
    secret = open(args.secret_file).read().strip()
    users, depts, roles, user_roles, perms, rps, uorg, customers, shares = parse_config(args.config, args.extra)
    customers = [c for c in customers[:args.max_customers] if not c.get('pool')]
    model = ScopeModel(users, depts, roles, user_roles, rps, uorg)
    report = {'at': time.strftime('%Y-%m-%dT%H:%M:%S'), 'base': args.base, 'users': [], 'summary': {}}
    for uid, u in sorted(users.items()):
        token = make_token(secret, uid, u['username'])
        s, info = http_get(args.base, '/api/auth/info', token)
        if s != 200 or not info or not info.get('data'):
            report['users'].append({'uid': uid, 'username': u['username'], 'error': f'auth/info {s}'})
            continue
        d = info['data']; effective = set(d.get('permissions') or [])
        role_ids = user_roles.get(uid, set())
        expected_perms = set().union(*[perms.get(r, set()) for r in role_ids]) if role_ids else set()
        missing = sorted(expected_perms - effective); extra = sorted(effective - expected_perms)
        role_codes = sorted(roles[r]['code'] for r in role_ids if r in roles)
        row = {'uid': uid, 'username': u['username'], 'roles': role_codes, 'dataScope': d.get('dataScope'),
               'permCount': len(effective), 'expectedPermCount': len(expected_perms),
               'missing': missing, 'extra': extra, 'r1_ok': not missing and not extra, 'customers': []}
        for c in customers:
            exp, scope, anchor = model.expected_customer(uid, c, effective, shares, args.include_pool)
            st, _ = http_get(args.base, f"/api/customer/{c['id']}", token)
            actual = (st == 200)
            ok = (actual == exp)
            row['customers'].append({'id': c['id'], 'owner': c['owner'], 'dept': c['dept'],
                                     'expected': exp, 'actual': actual, 'status': st, 'ok': ok,
                                     'scope': scope, 'anchor': anchor})
        report['users'].append(row)
    # summary
    all_rows = report['users']
    r1_bad = [r for r in all_rows if not r.get('r1_ok')]
    cust_rows = [c for r in all_rows for c in r.get('customers', [])]
    mism = [c for c in cust_rows if not c['ok']]
    report['summary'] = {
        'users': len(all_rows), 'r1_bad_users': len(r1_bad),
        'customer_cases': len(cust_rows), 'customer_mismatch': len(mism),
        'by_role': {rc: {'users': sum(1 for r in all_rows if rc in r.get('roles', [])),
                         'r1_bad': sum(1 for r in all_rows if rc in r.get('roles', []) and not r.get('r1_ok')),
                         'cust_mismatch': sum(1 for r in all_rows if rc in r.get('roles', []) for c in r.get('customers', []) if not c['ok'])}
                    for rc in sorted({rc for r in all_rows for rc in r.get('roles', [])})},
    }
    import os
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    json.dump(report, open(args.out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(json.dumps(report['summary'], ensure_ascii=False, indent=1))
    print('r1_bad_samples:', [(r['username'], r['missing'][:5], r['extra'][:5]) for r in r1_bad[:5]])
    print('cust_mismatch_samples:', mism[:8])
    return 1 if r1_bad or mism else 0


if __name__ == '__main__':
    raise SystemExit(main())
