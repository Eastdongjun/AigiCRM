#!/usr/bin/env python3
# 【P2-7 2026-10-19】小程序静态 class 定义契约:
# WXML 静态 class(整属性无 {{}} 的 token)必须在某 wxss 中有定义——
# 无定义=视觉静默退化(如 empty-icon 空态图标退化裸字符)或纯冗余 token 债。
# 动态属性整体跳过: {{}} 内的类名由运行时拼装, 静态不可判定, 其伴生静态属性仍会被本检查覆盖。
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "aigicrm-miniprogram"

def extract_used():
    used = {}
    for wxml in sorted(ROOT.glob("pages/**/*.wxml")):
        text = wxml.read_text(encoding="utf-8")
        for m in re.finditer(r'class="([^"]*)"', text):
            val = m.group(1)
            if "{{" in val:
                continue
            for tok in val.split():
                used.setdefault(tok, set()).add(wxml)
    return used

def extract_defined():
    defined = set()
    for wxss in sorted(ROOT.glob("pages/**/*.wxss")) + [ROOT / "app.wxss"]:
        text = re.sub(r"/\*.*?\*/", "", wxss.read_text(encoding="utf-8"), flags=re.S)
        for m in re.finditer(r"\.([A-Za-z_][\w-]*)", text):
            defined.add(m.group(1))
    return defined

def main():
    used = extract_used()
    defined = extract_defined()
    missing = {k: v for k, v in used.items() if k not in defined}
    if missing:
        print(f"FAIL: 小程序静态 class 无定义 {len(missing)} 个(需定义或删除冗余 token):")
        for k in sorted(missing):
            pages = sorted({str(p.relative_to(ROOT)) for p in missing[k]})
            print(f"  .{k}  <- {', '.join(pages)}")
        return 1
    print(f"PASS: 小程序静态 class 全部有定义(static={len(used)}, wxss-defined={len(defined)})")
    return 0

if __name__ == "__main__":
    sys.exit(main())
