#!/usr/bin/env python3
# ============================================================
# 敏感动作门禁覆盖契约(2026-11-05 用户令"其他模块有没有做这种全面的权限码"+拍板固化扫描器)
# ============================================================
# 检查什么: PC/MP 全部模块的按钮级敏感动作(导出/下载/删除/重置/撤销/移交/清空/停用/恢复/
#   强制/导入/踢下线/作废/确认收款/入海/还原/审批), 是否有按码门禁关联(动作行±7 行窗口内
#   出现 hasPermission/permission/can* 字样)。
# 为什么: 2026-11-05 四项激励审计抓出 G1(三页签零门禁)/G2(客户排行导出裸奔)——同类动作门禁
#   不一致此前无人知晓。本契约使"新增未挂码动作"即红。
# 判定口径:
#   · 命中行 ±7 行窗口内有按码关联 → 已挂码, 通过;
#   · 无按码关联 → 必须命中下方 ALLOW 白名单(逐条附分诊理由, 分诊=2026-11-05 全量人工过)——
#     白名单条目 = "合理动作": 视图操作(筛选/重置/页签)、本人数据动作、上层入口已按码的
#     对话框内部动作、权限推导联动、后端守卫兜底+当前全员持码(注记#1)。
# 反脆弱:
#   · 陈旧白名单: 白名单条目在扫描中不再命中(按钮被改/已挂码) → 提醒移除条目, 防白名单烂化;
#   · 总量护栏: 敏感动作总数低于下限 = 扫描器自身静默失配(假绿), 先修扫描器。
# 运行: python3 scripts/check-action-gate-coverage.py (dev-verify contracts 阶段调用)
# ============================================================
import re
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
UI = ROOT / 'aigicrm-ui/src/views'
MP = ROOT / 'aigicrm-miniprogram/pages'

VERBS = re.compile(
    r'(导出|下载|删除|重置|撤销|移交|清空|停用|恢复|强制|导入|踢下线|作废|确认收款|入海|还原|驳回|通过|捞取|转移|审批)')
BTN = re.compile(
    r'<el-button|<button|@click="(?:do|handle|on)?(?:export|download|delete|remove|reset|approve|reject|revoke|transfer|import|claim|release|purge|restore|void|confirm)')

# ---- 白名单(文件名 → [(动作行片段, 理由)]; 片段=动作行 strip 后任意位置的包含式匹配) ----
ALLOW = {
    'approval/index.vue': (
        [("router.push", "审批页签条件跳转(视图导航)"),
         ("confirmExtend", "审批通过动作按钮: 后端 approval:approve 守卫兜底+当前全员持码(注记#1)")],
        "审批管理: 通过/驳回按钮前端不按码隐藏=已知取舍(后端兜底), 详见注记#1"),
    'dashboard/index.vue': (
        [("doApprove(item.id)", "工作台快卡通过/驳回: 后端 approval:approve 兜底+当前全员持码(注记#1)"),
         ("doReject(item.id)", "同上")],
        "工作台审批快卡"),
    'customer/ImportDialog.vue': (
        [("downloadTemplate", "对话框内部动作: 入口按钮已按 customer:import 门禁(TopBar)"),
         ("doImport", "同上")],
        "客户批量导入对话框"),
    'customer/MainTable.vue': (
        [("hasActiveFilter", "清空筛选=视图操作")],
        "客户列表"),
    'customer/TopBar.vue': (
        [("customer:import", "已按码(局部 hasPerm 封装, 扫描器未识别=误报)"),
         ("customer:export", "已按码(同上)")],
        "客户顶栏导入/导出"),
    'customer/detail/InfoTab.vue': (
        [("v-if=\"!readonly\"", "转移按钮: readonly 由编辑权(customer:edit)推导联动")],
        "客户详情转移"),
    'incentive/SearchLightbox.vue': (
        [("downloadCurrent", "已可见数据的下载(激励查询分享制数据, dlToken 链口径)")],
        "激励查询灯箱下载"),
    'incentive/search.vue': (
        [("doReset", "重置筛选=视图操作")],
        "激励查询"),
    'reminder/index.vue': (
        [("handleClearAll", "一键处理=本人提醒数据处理(数据面天然本人)")],
        "提醒管理"),
    'score/entry.vue': (
        [("handleExport", "学分录入页导出: 页面码(score:entry)兜底, 无独立导出码=与多数列表同惯例(注记#2)")],
        "学分录入导出"),
    'sea/index.vue': (
        [("statusFilter", "状态页签=视图操作"),
         ("resetFilters", "重置筛选=视图操作"),
         ("下载模板", "对话框内部动作: 公海导入入口已按 sea:import 门禁(canImport)"),
         ("showImportDialog=false", "对话框关闭按钮")],
        "公海管理导入/筛选"),
    'system/PermGrantDialog.vue': (
        [("clearUsers", "对话框内部动作: 上层添加授权入口已按 system:config:edit 门禁"),
         ("clearEditUsers", "对话框内部动作(清编辑区弹窗勾选, 无后端效果): 上层入口已按 system:config:edit 门禁"),
         ("clearDeleteUsers", "对话框内部动作(清删除区弹窗勾选, 无后端效果): 上层入口已按 system:config:edit 门禁")],
        "激励专项授权对话框(2026-11-05 公海式分区: 编辑/删除区清空钮同批)"),
    'system/SeaConfigManager.vue': (
        [("clearSeaSelection", "配置器内部动作(上层保存链守卫)"),
         ("clearSecSelection", "配置器内部动作(上层保存链守卫)")],
        "公海池配置器"),
    'system/recycle.vue': (
        [("contentAvailable", "回收站文件内容查看: recycle:view 页面码兜底")],
        "回收站"),
}
MP_ALLOW = {
    'approval/detail/index.wxml': (
        [("showRejectAction", "MP 审批按钮: canApprove/canReject 推导+后端守卫兜底(注记#1)"),
         ("showApproveAction", "MP 审批按钮: 同上推导")],
        "MP 审批详情"),
    'approval/index.wxml': (
        [("showRejectAction", "MP 审批列表按钮: 同上推导"),
         ("showApproveAction", "MP 审批列表按钮: 同上推导")],
        "MP 审批列表"),
    'customer/detail/index.wxml': (
        [("showDeleteButton", "客户删除按钮: 权限推导(2026-11-04 实测他人记录无删除钮)")],
        "MP 客户详情删除"),
    'customer/filter/index.wxml': (
        [("reset", "重置筛选=视图操作")],
        "MP 客户筛选"),
    'customer/index.wxml': (
        [("showReset", "重置筛选=视图操作")],
        "MP 客户列表"),
    'incentive/detail/index.wxml': (
        [("previewLoading", "本人/授权记录的附件预览"),
         ("showDeleteAction", "本人/授权记录删除(权限推导)")],
        "MP 激励详情"),
    'incentive/home/index.wxml': (
        [("resetRecordFilters", "清空筛选=视图操作")],
        "MP 四项服务首页"),
    'incentive/index.wxml': (
        [("resetRecordFilters", "清空筛选=视图操作"),
         ("downloadStatDetailAtt", "已可见数据的附件下载")],
        "MP 四项服务"),
    'incentive/upload/index.wxml': (
        [("fileAction", "本人上传文件的预览/删除动作")],
        "MP 激励上传"),
    'profile/feedback/index.wxml': (
        [("clearDraft", "清空反馈草稿=视图操作")],
        "MP 反馈页"),
    'reminder/index.wxml': (
        [("processAll", "一键处理=本人提醒数据处理")],
        "MP 提醒"),
    'statistics/index.wxml': (
        [("text-btn", "审批摘要跳转=视图导航")],
        "MP 统计分析"),
}

# ---- 扫描(与 2026-11-05 审计同逻辑) ----
def scan_ui():
    hits, stale = [], []
    allowed_keys = {f: {frag for frag, _ in items} for f, (items, _reason) in ALLOW.items()}
    for f in sorted(UI.rglob('*.vue')):
        name = str(f.relative_to(UI))
        text = f.read_text(encoding='utf-8', errors='ignore')
        lines = text.splitlines()
        for i, l in enumerate(lines):
            if '<script' in l:
                break
            if l.strip().startswith('<!--'):
                continue
            if not (VERBS.search(l) and (BTN.search(l) or re.search(r'@click="[a-zA-Z]', l))):
                continue
            total[0] += 1
            window = '\n'.join(lines[max(0, i - 7):i + 7])
            if re.search(r'hasPermission\(|v-if="[^"]*(permission|Perm|can[A-Z])', window):
                # 已挂码; 顺带校验白名单条目是否已陈旧
                for frag in allowed_keys.get(name, ()):
                    if frag in l:
                        stale.append((name, frag))
                continue
            # 未挂码 → 必须命中白名单
            if any(frag in l for frag in allowed_keys.get(name, ())):
                continue
            unexplained.append((name, i + 1, l.strip()[:70]))
    return stale

total = [0, 0]
unexplained = []
stale = []

def scan_mp():
    mp_allowed_keys = {f: {frag for frag, _ in items} for f, (items, _reason) in MP_ALLOW.items()}
    for f in sorted(MP.rglob('*.wxml')):
        name = str(f.relative_to(MP))
        text = f.read_text(encoding='utf-8', errors='ignore')
        lines = text.splitlines()
        for i, l in enumerate(lines):
            if not (VERBS.search(l) and (BTN.search(l) or re.search(r'@click="[a-zA-Z]', l))):
                continue
            if l.strip().startswith('<!--'):
                continue
            total[1] += 1
            window = '\n'.join(lines[max(0, i - 3):i + 3])
            if re.search(r'wx:if="[^"]*(permission|Perm|can[A-Z])', window):
                for frag in mp_allowed_keys.get(name, ()):
                    if frag in l:
                        stale.append((name, frag))
                continue
            if any(frag in l for frag in mp_allowed_keys.get(name, ())):
                continue
            unexplained.append((name, i + 1, l.strip()[:70]))

scan_ui()
scan_mp()

print(f"敏感动作行: PC={total[0]} MP={total[1]} (护栏下限 PC≥55 MP≥18)")
if total[0] < 55 or total[1] < 18:
    print("FAIL: 敏感动作总数低于护栏下限——扫描器自身静默失配(假绿), 先修扫描器")
    sys.exit(1)

if unexplained:
    print(f"FAIL: {len(unexplained)} 个敏感动作无按码门禁且不在白名单:")
    for name, lineno, snippet in unexplained:
        print(f"  {name}:{lineno} | {snippet}")
    print("处置: 属合理动作 → 连同理由加入 ALLOW/MP_ALLOW; 否则 → 补按码门禁(v-if hasPermission)")
    sys.exit(1)

for name, frag in stale:
    print(f"提醒: 白名单条目已不再命中(陈旧), 请人工复核后移除: {name} | {frag[:40]}")

print(f"PASS: 敏感动作门禁覆盖 = PC {total[0]} + MP {total[1]} 全部按码或白名单在册")
sys.exit(0)
