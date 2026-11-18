#!/usr/bin/env python3
# 【A3 夹具治理 2026-11-18，QC 第 6 轮系统性要求】
# H2 夹具建表 DDL 生成器：六类表列名从 V1__baseline 真实 DDL 摘录（回收站四列按服务 SELECT 增补），
# 终结手写夹具漂移（两连击教训：category/category_id、type/reminder_type、user_id/owner_id）。
# 用法: python3 scripts/gen-a3-fixture-ddl.py            # 打印生成 DDL
#       python3 scripts/gen-a3-fixture-ddl.py --check    # 与测试文件内嵌生成物比对, 漂移即非 0
# 类型映射: bigint→BIGINT / varchar(n)→VARCHAR(n) / datetime→DATETIME / int→INT / tinyint→INT / 其余→VARCHAR(200)
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
V1 = (ROOT / "aigicrm-backend/src/main/resources/db/migration/V1__baseline.sql").read_text()

# 表 → (V1 表名, 服务 SELECT 业务列(不含 id/回收站四列——用于校验列真实存在))
TABLES = {
    "biz_customer": ["customer_name", "owner_id", "dept_id"],
    "sys_user": ["username", "real_name", "dept_id", "status", "leave_status"],
    "biz_file": ["file_name", "file_scope", "folder_id", "uploader_id", "oss_key"],
    "biz_task": None,  # 不在 V1（后续迁移建表）——列以服务 SELECT 为准, 手工核对段见 DDL 注释
    "biz_incentive_record": ["user_id", "category_id", "customer_id"],
    "biz_reminder": ["customer_id", "owner_id", "reminder_type"],
}
RECYCLE_COLS = ["deleted INT DEFAULT 0", "deleted_at DATETIME", "deleted_by BIGINT", "deleted_reason VARCHAR(200)"]
H2_TYPE = {"bigint": "BIGINT", "datetime": "DATETIME", "int": "INT", "tinyint": "INT", "json": "TEXT", "text": "TEXT", "date": "DATE", "decimal": "DECIMAL(20,2)"}


def cols_of(table):
    m = re.search(r"CREATE TABLE `%s` \((.*?)\n\) ENGINE" % table, V1, re.S)
    if not m:
        return None
    out = []
    for line in m.group(1).splitlines():
        cm = re.match(r"\s*`(\w+)`\s+(\w+)(\(\d+\))?", line.strip())
        if not cm:
            continue
        name, typ, _ = cm.group(1), cm.group(2).lower(), cm.group(3)
        if name in ("id", "tenant_id", "created_at", "updated_at", "deleted"):
            continue  # 统一在生成器里按模板补
        out.append((name, typ))
    return out


def gen():
    lines = ["-- 【生成物·勿手改】scripts/gen-a3-fixture-ddl.py 生成（V1__baseline 列名摘录 + 回收站列增补）"]
    for t, biz in TABLES.items():
        cols = cols_of(t)
        body = []
        if cols is None:
            lines.append(f"-- {t}: 不在 V1（后续迁移建表）, 列以服务 SELECT 为准并经 information_schema 穷举核对（QC 第 6 轮口径）")
            body = [("title", "VARCHAR(200)"), ("assignee_id", "BIGINT"), ("customer_id", "BIGINT")] if t == "biz_task" else []
        else:
            body = [(n, H2_TYPE.get(x, "VARCHAR(200)")) for n, x in cols]
            # 服务 SELECT 业务列必须在 V1 摘录列中（防 SELECT 漂移的机器校验）
            names = {n for n, _ in cols}
            for b in (biz or []):
                if b not in names:
                    raise SystemExit(f"服务 SELECT 列 {t}.{b} 不在 V1 真实列中——SELECT 漂移, 修服务 SQL 而非夹具!")
        all_cols = ["id BIGINT AUTO_INCREMENT PRIMARY KEY"] + [f"{n} {ty}" for n, ty in body] + RECYCLE_COLS
        lines.append(f"CREATE TABLE IF NOT EXISTS {t} ({', '.join(all_cols)});")
    lines.append("CREATE TABLE IF NOT EXISTS biz_recycle_export (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, export_no VARCHAR(40), requested_by BIGINT, types_json TEXT, filter_json TEXT, status VARCHAR(20) DEFAULT 'pending', file_path VARCHAR(500), file_size BIGINT, checksum_sha256 VARCHAR(64), expires_at DATETIME, downloaded_at DATETIME, error_msg VARCHAR(500), created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    ddl = gen()
    if "--check" in sys.argv:
        test = (ROOT / "aigicrm-backend/src/test/java/com/aigicrm/module/system/service/RecycleExportServiceTest.java").read_text()
        good = all(l.strip() in test for l in ddl.splitlines() if l.strip() and not l.startswith("--"))
        print("PASS: 夹具 DDL 与生成物一致" if good else "FAIL: 夹具与生成物漂移——重跑生成器并替换测试文件内嵌 DDL")
        sys.exit(0 if good else 1)
    print(ddl)
