#!/usr/bin/env python3
"""客户域数据范围：条目级（ID 集合）比对，28 启用用户。

复用 scope_oracle.py 的 DB 导出与 oracle 语义；真实 API 拉全量 ID 后做集合差。
输出 evidence/customer-idset-compare.tsv 与 customer-idset-summary.md。
"""
import importlib.util
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EVID = os.path.join(BASE, "evidence")
spec = importlib.util.spec_from_file_location("so", os.path.join(BASE, "scripts", "scope_oracle.py"))
so = importlib.util.module_from_spec(spec)
spec.loader.exec_module(so)

DB = {c["id"]: c for c in so.customers}


def fetch_customer_rows(uid, username):
    token = so.make_token(uid, username)
    rows, page = [], 1
    while True:
        url = f"{so.API}/api/customer/list?current={page}&size=500"
        req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token, "Origin": so.API})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                body = json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            return None, f"HTTP{e.code}"
        data = body.get("data") or {}
        recs = data.get("records") or []
        rows.extend(recs)
        total = data.get("total") or 0
        if len(rows) >= total or not recs:
            break
        page += 1
    return rows, None


def main():
    ids = sorted(uid for uid in so.users if uid in so.ACTIVE)
    out, mism = [], []
    for uid in ids:
        u = so.users[uid]
        final, meta = so.expected_rows(uid)
        expected = {r["id"] for r in final}
        config_ids = set(meta.get("config_ids", []))
        rows, err = fetch_customer_rows(uid, u["username"])
        if err:
            out.append({"uid": uid, "username": u["username"], "type": meta["type"], "codes": meta["codes"],
                        "anchor": meta["anchor"], "expected": len(expected), "actual": err,
                        "extra": "", "missing": "", "config": len(config_ids), "config_missing": "",
                        "field_mismatch": "", "note": "API_ERROR"})
            mism.append(out[-1])
            continue
        actual = {r["id"] for r in rows}
        extra = actual - expected
        missing = expected - actual
        config_missing = config_ids - actual
        field_mismatch = []
        for r in rows:
            cid = r.get("id")
            db = DB.get(cid)
            if not db:
                continue
            for k in ("owner_id", "dept_id", "status"):
                if k in r and r.get(k) != db.get(k):
                    field_mismatch.append((cid, k, db.get(k), r.get(k)))
        rec = {"uid": uid, "username": u["username"], "type": meta["type"], "codes": meta["codes"],
               "anchor": meta["anchor"], "expected": len(expected), "actual": len(actual),
               "extra": len(extra), "missing": len(missing), "config": len(config_ids),
               "config_missing": len(config_missing), "field_mismatch": len(field_mismatch),
               "note": ""}
        out.append(rec)
        if extra or missing or field_mismatch or config_missing:
            mism.append(rec)

    cols = ["uid", "username", "type", "codes", "anchor", "expected", "actual", "extra", "missing",
            "config", "config_missing", "field_mismatch", "note"]
    with open(os.path.join(EVID, "customer-idset-compare.tsv"), "w", encoding="utf-8") as f:
        f.write("\t".join(cols) + "\n")
        for r in out:
            f.write("\t".join(str(r.get(c, "")) for c in cols) + "\n")
    with open(os.path.join(EVID, "customer-idset-summary.md"), "w", encoding="utf-8") as f:
        f.write("# 客户域条目级（ID 集合）比对 · 28 启用用户\n\n")
        f.write(f"- 生成：{time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("- 口径：oracle 最终可见集合 vs `GET /api/customer/list` 全量 ID；另比关键字段 owner_id/dept_id/status\n")
        f.write(f"- 用户数：{len(out)}；有不一致：{len(mism)}\n\n")
        f.write("| uid | 用户 | 范围 | codes | anchor | oracle | API | extra | missing | 配置集合少看 | 字段不一致 |\n")
        f.write("|---|---|---|---|---|---|---|---|---|---|---|\n")
        for r in out:
            f.write(f"| {r['uid']} | {r['username']} | {r['type']} | {r['codes']} | {r['anchor']} | "
                    f"{r['expected']} | {r['actual']} | {r['extra']} | {r['missing']} | {r['config_missing']} | {r['field_mismatch']} |\n")
        f.write("\n## 不一致明细\n\n")
        if not mism:
            f.write("无。28 用户 ID 集合与关键字段全部一致。\n")
        else:
            for r in mism:
                f.write(f"- uid={r['uid']} {r['username']}（{r['type']}/{r['codes']}/{r['anchor']}）："
                        f"oracle={r['expected']}，API={r['actual']}，extra={r['extra']}，missing={r['missing']}，"
                        f"配置集合少看={r['config_missing']}，字段不一致={r['field_mismatch']}\n")
    print(json.dumps({"users": len(out), "mismatch": len(mism)}, ensure_ascii=False))
    for r in out:
        print(r["uid"], r["username"], r["type"], r["codes"], r["anchor"], r["expected"], r["actual"],
              r["extra"], r["missing"], r["config_missing"], r["field_mismatch"], r["note"], sep="\t")


if __name__ == "__main__":
    main()
