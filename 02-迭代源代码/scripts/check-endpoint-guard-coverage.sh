#!/usr/bin/env bash
# ============================================================
# 接口守卫全量覆盖契约(2026-11-05 产品化批新增)
# 【为什么需要】此前接口权限完备性靠 5 个契约各盯各的家族(抽查), 新端点漏挂
#   @PreAuthorize 无人知晓。本契约做全量扫描: 每个端点必须 ①方法/类上显式
#   @PreAuthorize, 或 ②列入下方公开白名单(逐条附设计理由), 二者必居其一。
# 【方法】python 逐行窗口扫描(注解可能在 mapping 上方 5 行或下方 3 行),
#   类级 @PreAuthorize 视为该类全部端点已守卫。
# 【反脆弱】总端点数下限护栏(防扫描自身静默失配=假绿);
#   白名单条目若已挂上注解则报"陈旧白名单"(提示从白名单移除)。
# 【口径】"公开"=仅依赖认证链(登录即可)或纯自服务/匿名设计接口;
#   方法内程序化守卫(如 ScheduledTaskController 的 isAdmin)视同已守卫并注明。
# ============================================================
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- 公开白名单(文件名 + 映射注解原文; 理由必须写明, 无理由不入册) ----
WHITELIST=$(cat <<'EOS'
AuthController.java @PostMapping("/login")|登录入口, 匿名设计
AuthController.java @GetMapping("/info")|登录后身份/权限下发(认证链保护)
AuthController.java @PostMapping("/logout")|登出自服务
AuthController.java @PostMapping("/switch-group")|本人切集团(自服务, service 内校验成员关系)
AuthController.java @PostMapping("/forgot-password/lookup")|找回密码三段式, 匿名+验证码
AuthController.java @PostMapping("/forgot-password/send-code")|找回密码, 匿名+SMS限流
AuthController.java @PostMapping("/forgot-password/reset")|找回密码, 匿名+验证码校验
SysConfigController.java @GetMapping("/public")|公开配置(登录页读), 探针基准接口
SysConfigController.java @GetMapping("/logo")|公开 logo
SysUserController.java @PostMapping("/change-password")|本人改自己密码(自服务)
FileController.java @GetMapping("/download-token")|短效下载令牌签发(本人自服务, 1h)
FileController.java @GetMapping("/download/public/**")|分享直链(设计公开, shareToken 校验)
IncentivePermissionController.java @GetMapping("/check")|查本人激励开关布尔值(自服务, 无他人数据)
ScopeController.java @GetMapping("/users")|可见人员下拉(按调用者数据范围过滤, 非全量枚举)
ScopeController.java @GetMapping("/depts")|可见部门树(按调用者数据范围过滤, 非全量枚举)
ScheduledTaskController.java @PostMapping("/check-expire")|方法内 SecurityUtils.isAdmin() 程序化守卫(M9 注释)
EOS
)

python3 - "$ROOT" "$WHITELIST" <<'PYEOF'
import re, pathlib, sys
root = pathlib.Path(sys.argv[1]) / 'aigicrm-backend/src/main/java'
wl = {}
for line in sys.argv[2].strip().splitlines():
    key, reason = line.split('|', 1)
    wl[key.strip()] = reason.strip()

total, unguarded, stale = 0, [], []
for f in root.rglob('*Controller.java'):
    fname = f.name
    lines = f.read_text(encoding='utf-8', errors='ignore').splitlines()
    cls_idx = next((j for j, x in enumerate(lines) if re.search(r'\bclass\s+\w+', x)), -1)
    cls_has = cls_idx >= 0 and any('@PreAuthorize' in lines[k] for k in range(max(0, cls_idx - 4), cls_idx + 1))
    for i, l in enumerate(lines):
        m = re.match(r'\s*(@(?:Get|Post|Put|Delete|Patch)Mapping(?:\([^)]*\))?)', l)
        if not m:
            continue
        total += 1
        ctx = lines[max(0, i - 5):i + 4]
        key = f"{fname} {m.group(1)}"
        if cls_has or any('@PreAuthorize' in w for w in ctx):
            if key in wl:
                stale.append(key)  # 白名单条目已挂上注解 = 陈旧, 应从白名单移除
            continue
        if key in wl:
            continue  # 设计公开, 在册放行
        unguarded.append((fname, l.strip(), (lines[i + 1].strip()[:60] if i + 1 < len(lines) else '')))

# 【死码清理 2026-11-08】护栏下限 230 → 227: 删 POST /incentive/records/recalculate(1)
# 与逐笔开票/回款 8 写端点后总数 236→227(删除属有意收缩, 非扫描失配)
# 【P0-2 2026-11-12】护栏下限 227 → 226: 退役休眠越权端点 GET /api/customer/rankings(有意收缩)
print(f"端点总数: {total} (护栏下限 226)")
if total < 226:
    print("FAIL: 端点总数低于护栏下限——扫描可能静默失配(假绿), 先修扫描再谈覆盖")
    sys.exit(1)
if unguarded:
    print(f"FAIL: {len(unguarded)} 个端点无 @PreAuthorize 且不在公开白名单:")
    for fname, mapping, sig in unguarded:
        print(f"  {fname} {mapping} :: {sig}")
    print("处置: 属设计公开的 → 连同理由加入本脚本白名单; 否则 → 补 @PreAuthorize 注册码")
    sys.exit(1)
for key in stale:
    print(f"提醒: 白名单条目已挂上注解(陈旧), 请从白名单移除: {key}")
print(f"PASS: 接口守卫全量覆盖 = {total} 端点全部显式守卫或在册公开白名单({len(wl)} 条, 陈旧 {len(stale)})")
sys.exit(0)
PYEOF
