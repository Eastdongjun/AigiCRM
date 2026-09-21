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
        # 【B8 2026-09-22 校准】全量 Controller 扫描 + 显式豁免清单(原只扫配置类却输出"所有Controller",
        # 声明范围大于实际——GPT报告 TEST-P2-008); 豁免必须写明业务理由, 新增豁免需评审
        WAIVER = {
            # 提交审批门槛=查看门槛(产品口径: 能看到审批流即可发起, 对象级守卫在 Service)
            "ApprovalController.java": "submit+approval:view 为产品口径, 对象守卫在服务层",
            # 提醒处理: assertVisible 限本人范围内提醒, 拆码待产品决策(P2 队列)
            "ReminderController.java": "process+view 有本人对象守卫; ensure-expiring 已限admin; 拆码在P2队列",
            # 激励改/删: view 底线 + Controller 内 admin∨canEdit∨本人归属守卫(已审计闭环)
            "IncentiveController.java": "PUT/DELETE records+view 为底线码设计, 归属守卫在方法内(02-idor已核)",
            # 公海池删除: 写门禁是池级 ACL PERM_DELETE(SeaService.hasPoolPerm), 与文件夹 ACL 同族设计
            "SeaController.java": "DELETE /pool 用 sea:view + 池级ACL PERM_DELETE 双闸(池ACL即写粒度)",
        }
        if fn in WAIVER:
            print(f"  waiver: {fn} — {WAIVER[fn]}")
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
            # 【B8】view 判定精化: 含 and 的复合表达式(如 edit ∧ finance:view)是与关系收紧,
            # view 仅是必要条件之一, 不构成"view 即可写"的门禁, 视为合规; 仅纯 view(或 view 之间 OR)才违规
            view_only = "view" in perm and " and " not in perm
            if http == "GET" and "view" in perm:
                has_view_get = True
            elif http in ("POST", "PUT", "DELETE") and view_only:
                write_ops.append((http, perm))

        if has_view_get and write_ops:
            for http, perm in write_ops:
                VIOLATIONS.append(f"{path}: {http} 使用查看权限 {perm}(同一文件存在 view 的 GET,写操作应为 edit/add/delete 粒度)")

if VIOLATIONS:
    print("❌ 权限粒度检查失败:")
    for v in VIOLATIONS:
        print(f"  - {v}")
    sys.exit(1)
print("✅ 权限粒度检查通过: 全量 Controller 扫描(配置类强制edit粒度, 豁免见上方waiver清单)")
