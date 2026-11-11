#!/usr/bin/env python3
# 【底座收敛 P1 第一步 2026-11-11 → T9 2026-11-12 修正】权限码生成物: 后端权威集(注解+命令式) → 双端常量文件。
# 三端手写字面量(PC 51/MP 30/后端 78)是历次漂移的根源; 本生成器建立"单一权威源→生成物"管道,
# 前端消费改造(hasPermission(PERMISSION_CODES.FILE_UPLOAD) 替换字面量)分批进行——
# 手写字面量在替换完成前仍由 check-permission-code-consumption.sh 拦截漂移。
# 【T9 修复 D8 漏码】①正则改抓参数段整体再逐码提取——原单码正则对 hasAnyAuthority('a','b')
#   多参形态整条不匹配(漏 system:user:add/delete/view、system:org:admin 等仅存于多参的码);
#  ②FE_ONLY 前端专属码白名单段——纯前端门控码(后端零消费点, 常量化会 undefined)独立区块输出。
# 用法: gen-permission-codes.py <backend_src_root>
import re
import sys
from pathlib import Path

# 【T9】前端专属码(后端注解/命令式零消费, 仅前端按钮门控): 常量化依赖此显式白名单, 新增须注明理由。
# 【T9 翻案记录】incentive:rank:view 曾被误登此处——真相=IncentiveController:159/166 的
# hasAnyAuthority('incentive:stats:view','incentive:rank:view') 多参形态被旧单码正则漏抓,
# 生成器修复后它回到后端权威集(71 码), 误判的 MP 回退已同步翻案。当前无真·纯前端码。
FE_ONLY_CODES = []

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
        body = "\n".join(lines)
        # 【T9】参数段整体捕获后逐码提取(单参/多参/命令式双引号全覆盖)
        for m in re.finditer(r"has(?:Any)?Authority\(([^)]*)\)", body):
            codes.update(re.findall(r"'([a-z][a-z0-9:_-]+)'", m.group(1)))
        for m in re.finditer(r'SecurityUtils\.hasAuthority\("([a-z][a-z0-9:_-]+)"\)', body):
            codes.add(m.group(1))
    assert codes, "权威码集为空(扫描器失灵?)"

    overlap = codes & set(FE_ONLY_CODES)
    assert not overlap, f"FE_ONLY 码已进入后端权威集, 应从白名单移除: {sorted(overlap)}"
    ordered = sorted(codes)

    def ident(code: str) -> str:
        return code.replace(":", "_").replace("-", "_").upper()

    banner = "// 由 scripts/gen-permission-codes.py 自动生成——禁止手改; 权威源=后端注解+命令式码 + FE_ONLY 前端专属码白名单。\n"
    pc = banner + "// PC(ESM): import { PERMISSION_CODES } from '@/generated/permission-codes'\n"
    pc += "export const PERMISSION_CODES = Object.freeze({\n"
    pc += "".join(f"  {ident(c)}: '{c}',\n" for c in ordered)
    if FE_ONLY_CODES:
        pc += "  // ---- FE_ONLY 前端专属码(后端不校验, 仅前端按钮门控; 新增须在 gen-permission-codes.py 白名单注明理由) ----\n"
        pc += "".join(f"  {ident(c)}: '{c}',\n" for c in sorted(FE_ONLY_CODES))
    pc += "})\n"
    ui = Path("aigicrm-ui/src/generated/permission-codes.js")
    ui.parent.mkdir(parents=True, exist_ok=True)
    ui.write_text(pc, encoding="utf-8")

    mp = banner + "// MP(CommonJS): const { PERMISSION_CODES } = require('../../generated/permission-codes')\n"
    mp += "const PERMISSION_CODES = Object.freeze({\n"
    mp += "".join(f"  {ident(c)}: '{c}',\n" for c in ordered)
    if FE_ONLY_CODES:
        mp += "  // ---- FE_ONLY 前端专属码(后端不校验, 仅前端按钮门控) ----\n"
        mp += "".join(f"  {ident(c)}: '{c}',\n" for c in sorted(FE_ONLY_CODES))
    mp += "})\nmodule.exports = { PERMISSION_CODES }\n"
    mp_path = Path("aigicrm-miniprogram/generated/permission-codes.js")
    mp_path.parent.mkdir(parents=True, exist_ok=True)
    mp_path.write_text(mp, encoding="utf-8")

    print(f"生成完成: {len(ordered)} 后端权威码 + {len(FE_ONLY_CODES)} FE_ONLY 前端专属码 -> {ui} / {mp_path}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "aigicrm-backend/src/main/java")
