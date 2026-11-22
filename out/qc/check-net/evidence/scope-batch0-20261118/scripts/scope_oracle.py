#!/usr/bin/env python3
"""AIGICRM 客户域数据范围独立对账（计数级 v1）。

原则：
- oracle 独立实现（只读 DB 导出 + 文档语义），不调用应用层 resolveScope；
- 先算「配置应见」与「配置+auto_share 应见」两档，再与真实 API 计数比对；
- 生产零写入；全部数据来自测试库只读导出。

用法：python3 scope_oracle.py
输出：evidence/scope-count-compare.tsv / evidence/scope-count-summary.md
"""
import base64
import collections
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(BASE, "data")
EVID = os.path.join(BASE, "evidence")
API = "http://111.229.37.220"
SECRET = b"dummy"

VIEW_LIKE = {"view", "stats_view", "export", "rank_view"}


def load_tsv(name):
    path = os.path.join(DATA, name + ".tsv")
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line:
                rows.append(line.split("\t"))
    return rows


def num(v):
    if v is None or v == "" or v == "NULL":
        return None
    return int(v)


# ---------- 装载 ----------
users = {}
for f in load_tsv("users"):
    uid = int(f[0])
    users[uid] = {
        "username": f[1], "status": num(f[2]), "org_id": num(f[3]),
        "dept_id": num(f[4]), "office_id": num(f[5]), "manager_id": num(f[6]),
        "deleted": num(f[7]), "override": f[8] if len(f) > 8 else "",
    }
roles = {}
for f in load_tsv("roles"):
    roles[int(f[0])] = {"role_code": f[1], "data_scope": f[2], "status": num(f[3])}
user_roles = collections.defaultdict(list)
for f in load_tsv("user_roles"):
    user_roles[int(f[0])].append(int(f[1]))
scopes = []
for f in load_tsv("scopes"):
    scopes.append({
        "id": int(f[0]), "role_code": f[1], "module": f[2], "action": f[3],
        "scope_level": f[4], "assignee_type": f[5], "assignee_key": f[6],
        "anchor": f[7] if len(f) > 7 else "OWNER",
    })
depts = {}
for f in load_tsv("depts"):
    depts[int(f[0])] = {
        "parent_id": num(f[1]), "dept_type": num(f[2]), "status": num(f[3]),
        "leader_id": num(f[4]), "group_id": num(f[5]),
    }
user_org = collections.defaultdict(list)
for f in load_tsv("user_org"):
    user_org[int(f[0])].append((int(f[1]), num(f[2]) or 0))
role_org = collections.defaultdict(list)
for f in load_tsv("role_org"):
    role_org[int(f[0])].append((int(f[1]), num(f[2]) or 0))
org_admin = collections.defaultdict(list)
for f in load_tsv("org_admin"):
    org_admin[num(f[0])].append(int(f[1]))
customers = []
for f in load_tsv("customers"):
    customers.append({
        "id": int(f[0]), "tenant_id": num(f[1]), "owner_id": num(f[2]),
        "dept_id": num(f[3]), "office_id": num(f[4]), "region_id": num(f[5]),
        "status": f[6], "deleted": num(f[7]), "group_id": num(f[8]),
        "legal_entity_id": num(f[9]), "dev_type": f[10] if len(f) > 10 else "",
        "expire_date": f[11] if len(f) > 11 else "",
    })
config = {}
for f in load_tsv("config"):
    config[f[0]] = f[1] if len(f) > 1 else ""
auto_share = json.loads(config.get("customer.auto_share") or "{}")
superior_on = str(config.get("perm.superior.see.subordinate", "false")).lower() == "true"
leader_on = str(config.get("perm.org.leader.see.org", "false")).lower() == "true"


# ---------- 基础集合 ----------
def active_users():
    return {uid for uid, u in users.items() if u["status"] == 1 and u["deleted"] == 0}


ACTIVE = active_users()


def user_role_codes(uid):
    return {roles[r]["role_code"] for r in user_roles.get(uid, []) if r in roles and roles[r]["status"] == 1}


def data_scopes(uid):
    u = users[uid]
    out = []
    for r in user_roles.get(uid, []):
        if r in roles and roles[r]["status"] == 1 and roles[r]["data_scope"]:
            out.append(roles[r]["data_scope"])
    if u["override"]:
        out.append(u["override"])
    return list(dict.fromkeys(out))


def children(dept_id):
    return [d for d, x in depts.items() if x["parent_id"] == dept_id and x["status"] == 1]


def collect_subtree(root):
    acc = {root}
    stack = [root]
    while stack:
        cur = stack.pop()
        for c in children(cur):
            if c not in acc:
                acc.add(c)
                stack.append(c)
    return acc


def users_in_depts(dept_ids):
    ids = {uid for uid, u in users.items() if uid in ACTIVE and u["dept_id"] in dept_ids}
    return ids


def find_ancestor(dept_id, dept_type):
    cur = dept_id
    for _ in range(20):
        if cur is None or cur not in depts:
            return None
        if depts[cur]["dept_type"] == dept_type:
            return cur
        cur = depts[cur]["parent_id"]
    return None


def resolve_team(uid):
    acc = {uid}
    level = [uid]
    for _ in range(5):
        nxt = [u for u in ACTIVE if users[u]["manager_id"] in level and u not in acc]
        if not nxt:
            break
        acc.update(nxt)
        level = nxt
    return acc


def resolve_led_orgs(uid):
    roots = [d for d, x in depts.items() if x["leader_id"] == uid and x["status"] == 1]
    dept_ids = set()
    for r in roots:
        dept_ids |= collect_subtree(r)
    out = users_in_depts(dept_ids)
    out.add(uid)
    return out


def is_team_scoped(uid):
    return "team" in data_scopes(uid)


def custom_refs(uid):
    refs = list(user_org.get(uid, []))
    for r in user_roles.get(uid, []):
        if r in roles and roles[r]["status"] == 1 and roles[r]["data_scope"] == "custom":
            refs += role_org.get(r, [])
    return refs


# ---------- OWNER 锚点：resolveSingle + appendSystemLevelScopes ----------
def resolve_single_owner(uid, code):
    u = users[uid]
    dept_id = u["dept_id"]
    if code == "all":
        return None
    if code == "self":
        return {uid}
    if code == "team":
        return resolve_team(uid)
    if code in ("dept", "dept_and_children"):
        return users_in_depts(collect_subtree(dept_id)) | {uid} if dept_id else {uid}
    if code == "office":
        a = find_ancestor(dept_id, 3)
        return users_in_depts(collect_subtree(a)) | {uid} if a else {uid}
    if code == "region":
        a = find_ancestor(dept_id, 2)
        return users_in_depts(collect_subtree(a)) | {uid} if a else {uid}
    if code == "custom":
        refs = custom_refs(uid)
        if not refs:
            return {uid}
        dept_ids = set()
        for org_id, expand in refs:
            dept_ids |= collect_subtree(org_id) if expand else {org_id}
        return users_in_depts(dept_ids) | {uid}
    if code == "org_and_children":
        dept_ids = collect_subtree(dept_id) if dept_id else set()
        for org_id in org_admin.get(uid, []):
            dept_ids |= collect_subtree(org_id)
        return users_in_depts(dept_ids) | {uid}
    if code == "org":
        return users_in_depts({dept_id}) | {uid} if dept_id else {uid}
    return {uid}


def append_system_owner(uid, base, action):
    if action not in VIEW_LIKE:
        return set(base)
    out = set(base)
    if superior_on and not is_team_scoped(uid):
        out |= resolve_team(uid)
    if leader_on:
        out |= resolve_led_orgs(uid)
    return out


# ---------- ORG 锚点：resolveDeptScope ----------
def resolve_dept_scope(uid, code):
    u = users[uid]
    dept_id = u["dept_id"]
    if code == "all":
        return None
    if code == "custom":
        refs = custom_refs(uid)
        if not refs:
            return set()
        dept_ids = set()
        for org_id, expand in refs:
            dept_ids |= collect_subtree(org_id) if expand else {org_id}
        return dept_ids
    if code in ("region", "office"):
        a = find_ancestor(dept_id, 2 if code == "region" else 3)
        return collect_subtree(a) if a else set()
    if code in ("dept", "dept_and_children", "org_and_children"):
        if not dept_id:
            return set()
        dept_ids = collect_subtree(dept_id)
        if code == "org_and_children":
            for org_id in org_admin.get(uid, []):
                dept_ids |= collect_subtree(org_id)
        return dept_ids
    if code == "org":
        return {dept_id} if dept_id else set()
    if code == "team":
        if not dept_id:
            return set()
        dept_ids = {users[x]["dept_id"] for x in resolve_team(uid) if users[x]["dept_id"]}
        dept_ids.add(dept_id)
        return dept_ids
    return {dept_id} if dept_id else set()


# ---------- 配置匹配 ----------
def matches(row, module, action):
    return (row["module"] in (module, "*")) and (row["action"] in (action, "*"))


def is_exact(row, module, action):
    return row["module"] == module and row["action"] == action


def winning_config(uid, module="customer", action="view"):
    type_order = ["USER", "POSITION", "ROLE"]
    role_codes = user_role_codes(uid)
    for typ in type_order:
        rows = [r for r in scopes if r["assignee_type"] == typ and matches(r, module, action)
                and ((typ == "USER" and r["assignee_key"] == str(uid))
                     or (typ == "ROLE" and r["assignee_key"] in role_codes)
                     or typ == "POSITION")]
        if rows:
            exact = [r for r in rows if is_exact(r, module, action)]
            chosen = exact if exact else rows
            return typ, [r["scope_level"] for r in chosen]
    return None, []


def anchor_of(uid, module="customer", action="view"):
    role_codes = user_role_codes(uid)
    for typ in ["USER", "ROLE"]:
        for r in scopes:
            if r["assignee_type"] != typ:
                continue
            if typ == "USER" and r["assignee_key"] != str(uid):
                continue
            if typ == "ROLE" and r["assignee_key"] not in role_codes:
                continue
            if is_exact(r, module, action):
                return "ORG" if str(r["anchor"]).upper() == "ORG" else "OWNER"
    return "OWNER"


def scope_codes(uid, module="customer", action="view"):
    typ, codes = winning_config(uid, module, action)
    if codes:
        return typ, codes
    ds = data_scopes(uid)
    if len(ds) > 1:
        return None, ds
    return None, [ds[0] if ds else "self"]


# ---------- 比对 ----------
def apply_auto_share(uid, rows, is_all):
    """applyAutoShare：非 all 才追加；返回 (rows, share_ids, note)。"""
    if is_all or not auto_share:
        return rows, set(), "all/no-config"
    share = {uid}
    if auto_share.get("superior"):
        share |= {u for u in ACTIVE if users[u]["manager_id"] == uid}
    roles_cfg = [str(x) for x in (auto_share.get("roles") or [])]
    if roles_cfg:
        my_roles = user_role_codes(uid)
        if my_roles & set(roles_cfg):
            share |= {u for u in ACTIVE if users[u]["org_id"] == users[uid]["org_id"]}
    users_cfg = []
    for x in (auto_share.get("users") or []):
        try:
            users_cfg.append(int(x))
        except (TypeError, ValueError):
            pass
    if uid in users_cfg:
        return rows, set(), "exempt"
    if len(share) <= 1:
        return rows, share, "no-share"
    return [r for r in rows if r["owner_id"] in share], share, "narrowed"


def expected_rows(uid):
    typ, codes = scope_codes(uid)
    anchor = anchor_of(uid)
    base = [r for r in customers if r["deleted"] == 0 and r["tenant_id"] == 1
            and r["status"] not in ("sea", "archived")]
    is_all = False
    if anchor == "ORG":
        dept_ids = None
        for code in codes:
            part = resolve_dept_scope(uid, code)
            if part is None:
                dept_ids = None
                break
            dept_ids = (set(part) if dept_ids is None else dept_ids | set(part))
        is_all = dept_ids is None
        if not is_all:
            base = [r for r in base if r["dept_id"] in dept_ids]
    else:
        vis = None
        for code in codes:
            part = resolve_single_owner(uid, code)
            if part is None:
                vis = None
                break
            part = append_system_owner(uid, part, "view")
            vis = (set(part) if vis is None else vis | set(part))
        is_all = vis is None
        if not is_all:
            base = [r for r in base if r["owner_id"] in vis]
    config_count = len(base)
    final, share, note = apply_auto_share(uid, base, is_all)
    return final, {
        "type": typ or "DEFAULT", "codes": ",".join(codes), "anchor": anchor,
        "config_count": config_count, "final_count": len(final),
        "share_n": len(share), "note": note, "is_all": is_all,
        "config_ids": [r["id"] for r in base], "final_ids": [r["id"] for r in final],
    }


def expected(uid):
    return expected_rows(uid)[1]


TODAY = time.strftime("%Y-%m-%d")
TAB_ORDER = ["全部", "成熟创新", "突破开拓", "铺垫宣传", "新建改建扩建大修", "预警中", "已超期"]


def expected_tabs(rows):
    out = {"全部": len(rows)}
    for t in TAB_ORDER[1:5]:
        out[t] = sum(1 for r in rows if r.get("dev_type") == t)
    out["预警中"] = sum(1 for r in rows if r["status"] == "warning")
    out["已超期"] = sum(1 for r in rows if r.get("expire_date") and r["expire_date"] < TODAY)
    return out


def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=")


def make_token(uid, username):
    now = int(time.time())
    h = b64(b'{"alg":"HS256","typ":"JWT"}')
    p = b64(json.dumps({"jti": f"scope-check-{uid}-{now}", "sub": str(uid),
                        "username": username, "iat": now, "exp": now + 7200},
                       separators=(",", ":")).encode())
    return (h + b"." + p + b"." + b64(hmac.new(SECRET, h + b"." + p, hashlib.sha256).digest())).decode()


def actual_counts(uid, username):
    token = make_token(uid, username)
    url = API + "/api/customer/list/counts-by-filter"
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token, "Origin": API})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            body = json.loads(r.read().decode())
        return body.get("data", {})
    except urllib.error.HTTPError as e:
        return {"_error": f"HTTP{e.code}"}


def main():
    os.makedirs(EVID, exist_ok=True)
    ids = sorted(uid for uid in users if uid in ACTIVE)
    out = []
    tab_rows = []
    for uid in ids:
        u = users[uid]
        final, exp = expected_rows(uid)
        exp_tabs = expected_tabs(final)
        act_tabs = actual_counts(uid, u["username"])
        act = act_tabs.get("全部") if isinstance(act_tabs, dict) else None
        for tab in TAB_ORDER:
            e = exp_tabs[tab]
            a = act_tabs.get(tab) if isinstance(act_tabs, dict) else None
            d = (a - e) if isinstance(a, int) else ""
            tab_rows.append({"uid": uid, "username": u["username"], "tab": tab,
                             "expected": e, "actual": "" if a is None else a, "diff": d})
        out.append({
            "uid": uid, "username": u["username"],
            "roles": ",".join(sorted(user_role_codes(uid))),
            **exp, "actual": act if act is not None else "",
            "diff_config": (act - exp["config_count"]) if isinstance(act, int) else "",
            "diff_final": (act - exp["final_count"]) if isinstance(act, int) else "",
            "set_missing": len(set(exp.get("config_ids", [])) - set(exp.get("final_ids", []))),
            "set_extra": len(set(exp.get("final_ids", [])) - set(exp.get("config_ids", []))),
        })
    cols = ["uid", "username", "roles", "type", "codes", "anchor", "config_count",
            "final_count", "actual", "diff_config", "diff_final", "share_n", "note",
            "set_missing", "set_extra"]
    with open(os.path.join(EVID, "scope-count-compare.tsv"), "w", encoding="utf-8") as f:
        f.write("\t".join(cols) + "\n")
        for row in out:
            f.write("\t".join(str(row.get(c, "")) for c in cols) + "\n")
    with open(os.path.join(EVID, "scope-tab-compare.tsv"), "w", encoding="utf-8") as f:
        f.write("uid\tusername\ttab\texpected\tactual\tdiff\n")
        for row in tab_rows:
            f.write(f"{row['uid']}\t{row['username']}\t{row['tab']}\t{row['expected']}\t{row['actual']}\t{row['diff']}\n")

    mism = [r for r in out if r["diff_final"] != 0]
    tab_mism = [r for r in tab_rows if r["diff"] != 0]
    with open(os.path.join(EVID, "scope-count-summary.md"), "w", encoding="utf-8") as f:
        f.write("# 客户域数据范围计数比对（28 启用用户）\n\n")
        f.write(f"- 生成：{time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("- 口径：配置应见 vs 配置+auto_share 应见 vs API `counts-by-filter`\n")
        f.write(f"- auto_share：`{json.dumps(auto_share, ensure_ascii=False)}`\n")
        f.write(f"- 系统开关：superior={superior_on}, leader={leader_on}\n")
        f.write(f"- 用户数：{len(out)}；总数不一致：{len(mism)}；页签计数不一致：{len(tab_mism)}\n\n")
        f.write("| uid | 用户 | 角色 | 范围类型 | codes | anchor | 配置应见 | +auto_share | API实际 | diff(最终) | 集合少看 | 集合多看 | 说明 |\n")
        f.write("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        for r in out:
            f.write(f"| {r['uid']} | {r['username']} | {r['roles']} | {r['type']} | {r['codes']} | {r['anchor']} | "
                    f"{r['config_count']} | {r['final_count']} | {r['actual']} | {r['diff_final']} | "
                    f"{r['set_missing']} | {r['set_extra']} | {r['note']} |\n")
        f.write("\n## 页签计数不一致明细\n\n")
        if not tab_mism:
            f.write("无。全部 28 用户 × 7 页签一致。\n")
        else:
            for r in tab_mism:
                f.write(f"- uid={r['uid']} {r['username']} / {r['tab']}：应见={r['expected']}，实际={r['actual']}，差={r['diff']}\n")
        f.write("\n## 总数不一致明细\n\n")
        if not mism:
            f.write("无。\n")
        else:
            for r in mism:
                f.write(f"- uid={r['uid']} {r['username']}：配置应见={r['config_count']}，+auto_share={r['final_count']}，"
                        f"实际={r['actual']}，差={r['diff_final']}（{r['type']} / {r['codes']} / {r['anchor']}）\n")
    print(json.dumps({"users": len(out), "mismatch": len(mism), "tab_mismatch": len(tab_mism)}, ensure_ascii=False))
    for r in out:
        print(r["uid"], r["username"], r["roles"], r["type"], r["codes"], r["anchor"],
              r["config_count"], r["final_count"], r["actual"], r["diff_final"], r["note"], sep="\t")


if __name__ == "__main__":
    main()
