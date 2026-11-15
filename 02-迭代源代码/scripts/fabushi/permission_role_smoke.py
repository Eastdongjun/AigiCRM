#!/usr/bin/env python3
"""法布施 9 码定向权限冒烟：admin / 0 / 05 / 06 真实 token + 真实数据 ID。"""
import json, time, urllib.request, urllib.error, sys
from pathlib import Path
import jwt

BASE='http://111.229.37.220'
SECRET=Path('/tmp/jwt_secret.txt').read_text().strip()
USERS={1:'admin',11:'NO001',43:'NM100',38:'TY002'}

def tok(uid, name):
    n=int(time.time())
    return jwt.encode({'jti':f'role-smoke-{uid}-{n}','sub':str(uid),'username':name,'iat':n,'exp':n+3600},SECRET,algorithm='HS256')

def call(method,path,token=None,body=None):
    h={}
    if token: h['Authorization']='Bearer '+token
    data=None
    if body is not None:
        h['Content-Type']='application/json'; data=json.dumps(body).encode()
    req=urllib.request.Request(BASE+path,data=data,headers=h,method=method)
    try:
        with urllib.request.urlopen(req,timeout=10) as r: return r.status, r.read(400)
    except urllib.error.HTTPError as e: return e.code, e.read(200)
    except Exception as e: return f'ERR:{type(e).__name__}', b''

def main():
    rows=[]; failures=[]
    # 不含路径变量的端点：权限码断言最干净
    no_path=[
        ('GET','/api/fabushi/books',{'200'}),
        ('GET','/api/fabushi/books?status=all',{'200'}),
        ('GET','/api/fabushi/books/pending',{'admin'}),  # admin 200; others 403
        ('GET','/api/fabushi/posts?current=1&size=5',{'200'}),
        ('GET','/api/fabushi/stats/personal',{'200'}),
        ('GET','/api/fabushi/stats/team',{'admin','11'}),
        ('GET','/api/fabushi/stats/dashboard?period=week',{'admin','11'}),
        ('POST','/api/fabushi/posts',{'200','400'}),
        ('POST','/api/fabushi/books',{'200','400'}),
    ]
    for uid,name in USERS.items():
        token=tok(uid,name)
        # 拿真实权限集合，避免猜
        st,body=call('GET','/api/auth/info',token)
        try: perms=set(json.loads(body)['data'].get('permissions') or [])
        except Exception: perms=set()
        for method,path,allow in no_path:
            st,_=call(method,path,token, {} if method=='POST' else None)
            # pending/team/dashboard 的期望：admin/0 有码；05/06 无码
            if path.endswith('/pending'):
                allowed = name=='admin'
                ok = (st==200) if allowed else (st in (401,403))
            elif '/stats/team' in path or '/stats/dashboard' in path:
                allowed = name in ('admin','NO001')
                ok = (st==200) if allowed else (st in (401,403))
            elif path == '/api/fabushi/books?status=all':
                # 非 manage 仍 200，但只返回 approved；管理端含 disabled
                ok = st==200
            else:
                ok = (st in (200,400)) if method in ('POST',) else st==200
            rows.append({'user':name,'method':method,'path':path,'status':st,'perms':sorted(perms)[:5]})
            if not ok: failures.append(rows[-1])
    # 路径变量/管理动作：用真实/不存在 ID 验证权限门
    path_cases=[
        ('GET','/api/fabushi/books/3',{'200'},None),
        ('GET','/api/fabushi/books/1',{'admin'},None),  # disabled 仅 manage
        ('PUT','/api/fabushi/posts/999999/offline',{'admin'},{}),
        ('PUT','/api/fabushi/posts/999999/restore',{'admin'},{}),
        ('PUT','/api/fabushi/books/999999/approve',{'admin'},{}),
        ('POST','/api/fabushi/books/999999/pdf',{'admin'},{'fileId':999999}),
    ]
    for uid,name in USERS.items():
        token=tok(uid,name)
        for method,path,allowed,body in path_cases:
            st,_=call(method,path,token,body)
            allowed_user = name=='admin' or ('11' if name=='NO001' else '') in allowed and '11' in allowed
            # 只有 admin 和 0 具备 book:manage? 实际 seed: admin only; 0 lacks. 以权限码为准:
            if path == '/api/fabushi/books/3':
                ok = st == 200  # approved 全员可读
            elif name!='admin':
                ok = st in (401,403,404)  # 404 也允许(不暴露对象)
            else:
                ok = st not in (401,403)
            rows.append({'user':name,'method':method,'path':path,'status':st})
            if not ok: failures.append(rows[-1])
    print(json.dumps({'cases':len(rows),'failures':failures,'sample':rows[:8]},ensure_ascii=False,indent=1))
    Path('test-results/permission-matrix/fabushi-role-smoke.json').write_text(json.dumps({'cases':len(rows),'failures':failures,'rows':rows},ensure_ascii=False,indent=1),encoding='utf-8')
    return 0 if not failures else 1

if __name__=='__main__': raise SystemExit(main())
