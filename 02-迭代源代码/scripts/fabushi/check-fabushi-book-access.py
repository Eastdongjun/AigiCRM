#!/usr/bin/env python3
"""法布施书籍 PDF 账号级回归（R2-FABUSHI-SCOPE-001 防复发）。

目标：所有运行期应持有 fabushi:view 的启用账号，都能下载“已审核书籍”的 PDF；
无该功能码的账号必须被拒绝。账号与书目全部从被测库动态查询，不硬编码任何账号/文件 ID。

数据源与运行期同口径：
  - 账号：sys_user × sys_user_role × sys_role(status=1) × sys_role_menu × sys_menu(status=1)
          且 sys_menu.permission='fabushi:view'，sys_user.deleted=0 AND status=1；
  - 书目：biz_fabushi_book.status='approved' AND deleted=0 AND pdf_file_id 指向未删 biz_file；
  - 反例：启用、非 admin 角色、且无 fabushi:view 的账号（最多 3 个）。

用法（mysql 命令通过 shell 执行，SQL 从 stdin 读入）：
  python3 scripts/fabushi/check-fabushi-book-access.py \
    --base http://111.229.37.220 \
    --jwt-secret-file /tmp/jwt_secret.txt --alg HS256 \
    --mysql 'ssh aigicrm-test "PW=$(docker inspect shared-mysql --format ...); docker exec -e MYSQL_PWD=\"$PW\" -i shared-mysql mysql -uroot -N -B aigicrm_prod_20261016"'

生产同理（库名 ycmn / 容器 ycmn-mysql / --alg HS384，密钥只从 env 或文件读取，禁止写入仓库）。
退出码：0=全部通过；1=存在失败；2=参数/环境错误。
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
from pathlib import Path

USER_SQL = """
SELECT DISTINCT u.id, u.username
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id AND r.status = 1
JOIN sys_role_menu rm ON rm.role_id = r.id
JOIN sys_menu m ON m.id = rm.menu_id AND m.status = 1
WHERE u.deleted = 0 AND u.status = 1 AND m.permission = 'fabushi:view'
ORDER BY u.id;
"""

NEGATIVE_SQL = """
SELECT u.id, u.username
FROM sys_user u
WHERE u.deleted = 0 AND u.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    JOIN sys_role r ON r.id = ur.role_id AND r.status = 1
    JOIN sys_role_menu rm ON rm.role_id = r.id
    JOIN sys_menu m ON m.id = rm.menu_id AND m.status = 1
    WHERE ur.user_id = u.id AND m.permission = 'fabushi:view'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur2
    JOIN sys_role r2 ON r2.id = ur2.role_id AND r2.status = 1
    WHERE ur2.user_id = u.id AND r2.role_code = 'admin'
  )
ORDER BY u.id
LIMIT 3;
"""

BOOK_SQL = """
SELECT b.id, b.title, b.pdf_file_id
FROM biz_fabushi_book b
JOIN biz_file f ON f.id = b.pdf_file_id AND f.deleted = 0
WHERE b.status = 'approved' AND b.deleted = 0
ORDER BY b.id DESC
LIMIT 1;
"""


def run_sql(mysql_cmd, sql):
    proc = subprocess.run(mysql_cmd, shell=True, input=sql, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    if proc.returncode != 0:
        raise RuntimeError(f"mysql 查询失败(exit={proc.returncode}): {proc.stderr.strip()[:500]}")
    rows = []
    for line in proc.stdout.splitlines():
        if not line.strip():
            continue
        rows.append(line.split("\t"))
    return rows


def _b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def token_for(secret, alg, uid, username):
    """HS256/384/512 签发（stdlib 实现，避免 gate 环境依赖 PyJWT）。"""
    digest = {"HS256": hashlib.sha256, "HS384": hashlib.sha384, "HS512": hashlib.sha512}[alg]
    now = int(time.time())
    header = {"alg": alg, "typ": "JWT"}
    payload = {"jti": f"fabushi-regress-{uid}-{now}", "sub": str(uid),
               "username": username, "iat": now, "exp": now + 1800}
    signing_input = (_b64url(json.dumps(header, separators=(",", ":")).encode())
                     + "." + _b64url(json.dumps(payload, separators=(",", ":")).encode()))
    sig = hmac.new(secret.encode(), signing_input.encode(), digest).digest()
    return signing_input + "." + _b64url(sig)


def download_status(base, file_id, token):
    """用 curl 取状态码（避免 Python 3.14 自带 SSL 无系统根证书导致假失败）。"""
    cmd = ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}", "--max-time", "20",
           "-H", "Authorization: Bearer " + token, "-H", "Range: bytes=0-0",
           f"{base.rstrip('/')}/api/file/download/{file_id}"]
    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              text=True, timeout=30)
    except Exception as e:  # noqa: BLE001
        return f"ERR:{type(e).__name__}"
    code = proc.stdout.strip()
    return int(code) if code.isdigit() else f"ERR:curl({proc.returncode})"


def main():
    ap = argparse.ArgumentParser(description="法布施书籍 PDF 账号级回归")
    ap.add_argument("--base", required=True, help="被测环境，如 http://111.229.37.220 或 https://www.aigicrm.com")
    ap.add_argument("--mysql", required=True, help="执行 MySQL 查询的命令（读 stdin，-N -B 输出制表符分列）")
    ap.add_argument("--jwt-secret-file", help="JWT 密钥文件")
    ap.add_argument("--jwt-secret-env", default="JWT_SECRET", help="JWT 密钥环境变量名（默认 JWT_SECRET）")
    ap.add_argument("--alg", default="HS256", choices=["HS256", "HS384", "HS512"], help="签名算法（测试服 HS256，生产 HS384）")
    ap.add_argument("--out", help="报告 JSON 输出路径")
    args = ap.parse_args()

    secret = None
    if args.jwt_secret_file:
        secret = Path(args.jwt_secret_file).read_text(encoding="utf-8").strip()
    if not secret:
        secret = os.environ.get(args.jwt_secret_env, "").strip()
    if not secret:
        print(f"缺少 JWT 密钥：--jwt-secret-file 或环境变量 {args.jwt_secret_env}", file=sys.stderr)
        return 2

    try:
        books = run_sql(args.mysql, BOOK_SQL)
        users = run_sql(args.mysql, USER_SQL)
        negatives = run_sql(args.mysql, NEGATIVE_SQL)
    except Exception as e:  # noqa: BLE001
        print(str(e), file=sys.stderr)
        return 2

    if not books:
        print("未找到已审核且挂载 PDF 的书籍，无法执行回归", file=sys.stderr)
        return 2
    book_id, title, file_id = books[0][0], books[0][1], books[0][2]
    if not users:
        print("未找到持 fabushi:view 的启用账号，无法执行回归", file=sys.stderr)
        return 2

    results, failures = [], []
    for uid, username in users:
        status = download_status(args.base, file_id, token_for(secret, args.alg, uid, username))
        ok = status in (200, 206)
        results.append({"user": username, "userId": uid, "expected": "allow", "status": status, "ok": ok})
        if not ok:
            failures.append(results[-1])

    for uid, username in negatives:
        status = download_status(args.base, file_id, token_for(secret, args.alg, uid, username))
        ok = status == 403
        results.append({"user": username, "userId": uid, "expected": "deny(403)", "status": status, "ok": ok})
        if not ok:
            failures.append(results[-1])

    report = {
        "base": args.base,
        "book": {"id": book_id, "title": title, "pdfFileId": file_id},
        "checked": len(results),
        "passed": len(results) - len(failures),
        "failures": failures,
        "rows": results,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    text = json.dumps(report, ensure_ascii=False, indent=1)
    print(text)
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(text + "\n", encoding="utf-8")
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
