#!/usr/bin/env python3
# W3 运行时探针（测试服本机执行；token 由 QC 本地签发后 scp，secret 不过网）
# 用法: python3 -I /tmp/qc-w3-probe.py <mode> [args]
#   auth-probe                    单 token 鉴权探活
#   approve-race <approval_id>    两 token 并发 approve 同一单
#   claim-race <customer_id>      两 token 并发公海捞取
#   double-submit <type> <customer_id>  同 token 连续两次提交（幂等）
import json, sys, time, urllib.request, urllib.error, threading

API = "http://127.0.0.1:8080"
TOKENS = "/tmp/qc-w3-tokens.tsv"   # uid \t username \t token

def load_tokens():
    d = {}
    for line in open(TOKENS, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 3:
            d[int(f[0])] = (f[1], f[2])
    return d

def call(path, token=None, method="GET", body=None):
    url = API + path
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read()[:160].decode(errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, (e.read()[:160].decode(errors="replace") if e.fp else "")
    except Exception as e:
        return -1, repr(e)[:120]

mode = sys.argv[1] if len(sys.argv) > 1 else "auth-probe"
T = load_tokens()

if mode == "auth-probe":
    if 1 not in T: print("NO_TOKEN_1"); sys.exit(2)
    code, body = call("/api/auth/info", T[1][1])
    print(f"AUTH_PROBE status={code} body={body[:120]!r}")
elif mode == "approve-race":
    aid = sys.argv[2] if len(sys.argv) > 2 else ""
    if not aid or 1 not in T or 11 not in T: print("APPROVE_RACE need id+tok1+tok11"); sys.exit(2)
    res = []
    def fire(t):
        res.append(call(f"/api/approval/{aid}/approve", t, "POST", {"comment": "w3-race"}))
    ts = [threading.Thread(target=fire, args=(T[1][1],)), threading.Thread(target=fire, args=(T[11][1],))]
    for x in ts: x.start()
    for x in ts: x.join()
    ok = sum(1 for c, _ in res if c == 200)
    print(f"APPROVE_RACE results={res} success_count={ok} expect=1")
elif mode == "claim-race":
    # 公海捞取 = POST /api/approval/submit {type:"claim", customerId}；两请求并发（同一用户两 token）
    cid = sys.argv[2] if len(sys.argv) > 2 else ""
    if not cid or 1 not in T: print("CLAIM_RACE need id+tok1"); sys.exit(2)
    res = []
    def fire(t):
        res.append(call("/api/approval/submit", t, "POST",
                        {"type": "claim", "customerId": int(cid), "actionAfterApprove": "", "extraData": "{}"}))
    ts = [threading.Thread(target=fire, args=(T[1][1],)), threading.Thread(target=fire, args=(T[1][1],))]
    for x in ts: x.start()
    for x in ts: x.join()
    ok = sum(1 for c, _ in res if c in (200, 201))
    print(f"CLAIM_RACE results={res} success_count={ok} expect=1 (pool pending_approval guard)")
elif mode == "double-submit":
    typ = sys.argv[2] if len(sys.argv) > 2 else "claim"
    cid = sys.argv[3] if len(sys.argv) > 3 else "0"
    if 1 not in T: print("NO_TOKEN_1"); sys.exit(2)
    body = {"type": typ, "customerId": int(cid), "actionAfterApprove": "", "extraData": "{}"}
    a = call("/api/approval/submit", T[1][1], "POST", body)
    b = call("/api/approval/submit", T[1][1], "POST", body)
    print(f"DOUBLE_SUBMIT first={a} second={b} expect=first-2xx+second-4xx")
else:
    print("UNKNOWN_MODE"); sys.exit(2)
