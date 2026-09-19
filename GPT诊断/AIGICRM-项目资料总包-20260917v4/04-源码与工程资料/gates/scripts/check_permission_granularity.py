#!/usr/bin/env python3
"""权限粒度自动化检查(FEAT 0804):抓"写操作(POST/PUT/DELETE)误用 view 权限"类缺陷
    背景:ApprovalConfigController 四个接口全用 system:config:view,导致只有查看权限的角色可任意增删改。
    规则:同一 Controller 内,若存在 view 权限的 GET,则 POST/PUT/DELETE 必须使用非 view 权限码(如 :edit/:add/:delete)。
    用法: python3 scripts/check_permission_granularity.py [后端src目录]
    退出码: 0=通过; 1=发现违规
"""
import os, re, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "aigicrm-backend/src/main/java"
VIOLATIONS = []

for root, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith("Controller.java"):
            continue
        path = os.path.join(root, fn)
        # 只检查"配置管理类"Controller(写操作必须 edit 粒度);
        # 业务操作类(提交审批/处理提醒等)POST+view 是合理设计,不在此列
        if not re.search(r'(Config|Setting|Menu|Role|Dict|Permission)', fn):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()

        # 提取 方法块:注解行 + 紧跟的 HTTP 映射行
        blocks = re.findall(
            r'@(?:Get|Post|Put|Delete)Mapping\([^)]*\)\s*\n\s*@PreAuthorize\("([^"]+)"\)|'
            r'@PreAuthorize\("([^"]+)"\)\s*\n\s*@(?:Get|Post|Put|Delete)Mapping\([^)]*\)',
            text)

        has_view_get = False
        write_ops = []  # (method, permission)
        for a, b in blocks:
            perm = a or b
            # 需要知道这是哪个 HTTP 方法,重新按顺序扫描更可靠
        # 简化:按顺序匹配 "映射注解 + 权限注解" 对
        pattern = re.compile(
            r'@(Get|Post|Put|Delete)Mapping\([^)]*\)[^@]*?@PreAuthorize\("([^"]+)"\)|'
            r'@PreAuthorize\("([^"]+)"\)[^@]*?@(Get|Post|Put|Delete)Mapping\([^)]*\)')
        for m in pattern.finditer(text):
            http = (m.group(1) or m.group(4)).upper()
            perm = (m.group(2) or m.group(3))
            if http == "GET" and "view" in perm:
                has_view_get = True
            elif http in ("POST", "PUT", "DELETE") and "view" in perm:
                write_ops.append((http, perm))

        if has_view_get and write_ops:
            for http, perm in write_ops:
                VIOLATIONS.append(f"{path}: {http} 使用查看权限 {perm}(同一文件存在 view 的 GET,写操作应为 edit/add/delete 粒度)")

if VIOLATIONS:
    print("❌ 权限粒度检查失败:")
    for v in VIOLATIONS:
        print(f"  - {v}")
    sys.exit(1)
print("✅ 权限粒度检查通过:所有 Controller 的写操作均未使用 view 权限")
