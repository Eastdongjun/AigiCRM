#!/usr/bin/env python3
# 【底座收敛 P1 第一步 2026-11-11】权限码生成物: 后端权威集(注解+命令式) → 双端常量文件。
# 三端手写字面量(PC 51/MP 30/后端 78)是历次漂移的根源; 本生成器建立"单一权威源→生成物"管道,
# 前端消费改造(hasPermission(PERMISSION_CODES.FILE_UPLOAD) 替换字面量)分批进行——
# 先让生成物就位并被契约钉住(check-permission-codes-generated.sh: 生成物与权威集一致),
# 手写字面量在替换完成前仍由 check-permission-code-consumption.sh 拦截漂移。
# 用法: gen-permission-codes.py <backend_src_root>
import re
import sys
from pathlib import Path

def main(backend_root: str) -> None:
    src = Path(backend_root)
    assert src.is_dir(), f"后端源码根不存在: {src}"

    codes = set()
    for f in src.rglob("*.java"):
        try:
            text = f.read_text(encoding="utf-8", errors="replace")
        except Exception:
            continue
        lines = [l for l in text.splitlines() if not l.lstrip().startswith(("*", "//", "/*"))]
        for m in re.finditer(r"has(?:Any)?Authority\('[a-z][a-z0-9:_-]+'\)", "\n".join(lines)):
            codes.add(re.search(r"'([a-z][a-z0-9:_-]+)'", m.group(0)).group(1))
        for m in re.finditer(r'SecurityUtils\.hasAuthority\("[a-z][a-z0-9:_-]+"\)', "\n".join(lines)):
            codes.add(re.search(r'"([a-z][a-z0-9:_-]+)"', m.group(0)).group(1))
    assert codes, "权威码集为空(扫描器失灵?)"
    ordered = sorted(codes)

    def ident(code: str) -> str:
        return code.replace(":", "_").replace("-", "_").upper()

    banner = "// 由 scripts/gen-permission-codes.py 自动生成——禁止手改; 权威源=后端注解+命令式码。\n"
    pc = banner + "// PC(ESM): import { PERMISSION_CODES } from '@/generated/permission-codes'\n"
    pc += "export const PERMISSION_CODES = Object.freeze({\n"
    pc += "".join(f"  {ident(c)}: '{c}',\n" for c in ordered)
    pc += "})\n"
    ui = Path("aigicrm-ui/src/generated/permission-codes.js")
    ui.parent.mkdir(parents=True, exist_ok=True)
    ui.write_text(pc, encoding="utf-8")

    mp = banner + "// MP(CommonJS): const { PERMISSION_CODES } = require('../../generated/permission-codes')\n"
    mp += "const PERMISSION_CODES = Object.freeze({\n"
    mp += "".join(f"  {ident(c)}: '{c}',\n" for c in ordered)
    mp += "})\nmodule.exports = { PERMISSION_CODES }\n"
    mp_path = Path("aigicrm-miniprogram/generated/permission-codes.js")
    mp_path.parent.mkdir(parents=True, exist_ok=True)
    mp_path.write_text(mp, encoding="utf-8")

    print(f"生成完成: {len(ordered)} 码 -> {ui} / {mp_path}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "aigicrm-backend/src/main/java")
