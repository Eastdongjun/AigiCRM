#!/usr/bin/env python3
"""二期阅读链/附件/章节/活动/导出的定向权限冒烟：admin/0/05/06 真实 token。"""
import json,time,urllib.request,urllib.error
from pathlib import Path
import jwt
BASE='http://111.229.37.220'; SECRET=Path('/tmp/jwt_secret.txt').read_text().strip()
USERS={1:'admin',11:'NO001',43:'NM100',38:'TY002'}
def tok(uid,name):
 n=int(time.time()); return jwt.encode({'jti':f'p2-{uid}-{n}','sub':str(uid),'username':name,'iat':n,'exp':n+3600},SECRET,algorithm='HS256')
def call(method,path,token=None,body=None):
 h={};
 if token: h['Authorization']='Bearer '+token
 data=None
 if body is not None: h['Content-Type']='application/json'; data=json.dumps(body).encode()
 req=urllib.request.Request(BASE+path,data=data,headers=h,method=method)
 try:
  with urllib.request.urlopen(req,timeout=10) as r: return r.status
 except urllib.error.HTTPError as e: return e.code
 except Exception as e: return f'ERR:{type(e).__name__}'
def main():
 rows=[]; fails=[]
 # (method,path,body,allowed_users)
 cases=[
  ('GET','/api/fabushi/shelf',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/settings',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/records',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/progress/3',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/posts/1',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/books/3/sections',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/activities',None,{'admin','NO001','NM100','TY002'}),
  ('GET','/api/fabushi/stats/export?period=week',None,{'admin'}),
  ('POST','/api/fabushi/activities',{}, {'admin'}),
  ('POST','/api/fabushi/books/3/sections',{}, {'admin'}),
  ('POST','/api/fabushi/posts/1/attachments',{}, {'admin'}),
  ('PUT','/api/fabushi/activities/999999/end',None,{'admin'}),
 ]
 for uid,name in USERS.items():
  token=tok(uid,name)
  for method,path,body,allowed in cases:
   st=call(method,path,token,body)
   ok = (st not in (401,403)) if name in allowed else (st in (401,403))
   row={'user':name,'method':method,'path':path,'status':st,'allowed':name in allowed}
   rows.append(row)
   if not ok: fails.append(row)
 out={'cases':len(rows),'failures':fails,'rows':rows}
 Path('test-results/permission-matrix/phase2-role-smoke.json').write_text(json.dumps(out,ensure_ascii=False,indent=1),encoding='utf-8')
 print(json.dumps({'cases':len(rows),'failures':len(fails),'detail':fails},ensure_ascii=False,indent=1))
 return 0 if not fails else 1
if __name__=='__main__': raise SystemExit(main())
