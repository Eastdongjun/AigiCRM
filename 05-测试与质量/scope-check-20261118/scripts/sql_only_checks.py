#!/usr/bin/env python3
"""AIGICRM SQL-only 全模块完整性扫描（测试库只读）。

- 只读 SELECT；不写库；不调 API；不碰生产。
- 每条检查返回 (check_id, 计数[, 样本])；0=通过，>0=发现。
- 缺表/缺列自动记为 SCHEMA_MISSING（对应 DEF-2 迁移漂移）。
- 输出 evidence/sql-only-checks.tsv 与 evidence/sql-only-report.md。
"""
import collections
import json
import os
import re
import subprocess

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(BASE, "data")
EVID = os.path.join(BASE, "evidence")

# ---------- schema inventory ----------
tables = set()
for line in open(os.path.join(DATA, "tables.tsv"), encoding="utf-8"):
    t = line.strip()
    if t:
        tables.add(t)
cols = collections.defaultdict(set)
for line in open(os.path.join(DATA, "columns.tsv"), encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    if len(f) >= 2:
        cols[f[0]].add(f[1])


def has(req):
    for r in req:
        t, c = r.split(".", 1)
        if t not in tables or c not in cols[t]:
            return False, r
    return True, ""


# ---------- checks ----------
# (id, module, severity, desc, sql, required columns, info?)
CHECKS = [
    # 认证/用户
    ("AUTH-01", "认证/用户", "P1", "启用用户部门缺失/无效",
     "SELECT 'AUTH-01',COUNT(*) FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id=d.id WHERE u.deleted=0 AND u.status=1 AND (u.dept_id IS NULL OR d.id IS NULL)",
     ["sys_user.dept_id", "sys_user.deleted", "sys_user.status", "sys_dept.id"], False),
    ("AUTH-02", "认证/用户", "P1", "启用用户组织缺失/无效",
     "SELECT 'AUTH-02',COUNT(*) FROM sys_user u LEFT JOIN sys_dept o ON u.org_id=o.id WHERE u.deleted=0 AND u.status=1 AND (u.org_id IS NULL OR o.id IS NULL)",
     ["sys_user.org_id", "sys_user.deleted", "sys_user.status", "sys_dept.id"], False),
    ("AUTH-03", "认证/用户", "P1", "用户-角色绑定指向不存在角色",
     "SELECT 'AUTH-03',COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON ur.role_id=r.id WHERE r.id IS NULL",
     ["sys_user_role.user_id", "sys_user_role.role_id", "sys_role.id"], False),
    ("AUTH-04", "认证/用户", "P2", "用户-角色重复绑定",
     "SELECT 'AUTH-04',COUNT(*) FROM (SELECT user_id,role_id FROM sys_user_role GROUP BY user_id,role_id HAVING COUNT(*)>1) t",
     ["sys_user_role.user_id", "sys_user_role.role_id"], False),
    ("AUTH-05", "认证/用户", "P1", "角色 data_scope 非法值",
     "SELECT 'AUTH-05',COUNT(*) FROM sys_role WHERE data_scope NOT IN ('all','custom','region','office','dept','org_and_children','org','team','self')",
     ["sys_role.data_scope"], False),
    ("AUTH-06", "认证/用户", "P1", "用户 data_scope_override 非法值",
     "SELECT 'AUTH-06',COUNT(*) FROM sys_user WHERE data_scope_override IS NOT NULL AND data_scope_override<>'' AND data_scope_override NOT IN ('all','custom','region','office','dept','org_and_children','org','team','self')",
     ["sys_user.data_scope_override"], False),
    ("AUTH-07", "认证/用户", "P1", "用户名重复",
     "SELECT 'AUTH-07',COUNT(*) FROM (SELECT username FROM sys_user GROUP BY username HAVING COUNT(*)>1) t",
     ["sys_user.username"], False),
    ("AUTH-08", "认证/用户", "P2", "已过期 token 黑名单残留（可清理）",
     "SELECT 'AUTH-08',COUNT(*) FROM sys_token_blacklist WHERE expires_at<NOW()",
     ["sys_token_blacklist.expires_at"], True),
    ("AUTH-09", "认证/用户", "P2", "启用用户中仍要求强制改密",
     "SELECT 'AUTH-09',COUNT(*) FROM sys_user WHERE deleted=0 AND status=1 AND pwd_reset_required=1",
     ["sys_user.pwd_reset_required", "sys_user.deleted", "sys_user.status"], True),
    # 组织
    ("ORG-01", "组织", "P1", "部门父节点不存在（非根）",
     "SELECT 'ORG-01',COUNT(*) FROM sys_dept d LEFT JOIN sys_dept p ON d.parent_id=p.id WHERE d.parent_id<>0 AND p.id IS NULL",
     ["sys_dept.parent_id", "sys_dept.id"], False),
    ("ORG-02", "组织", "P2", "启用部门闭包缺自身行",
     "SELECT 'ORG-02',COUNT(*) FROM sys_dept d LEFT JOIN sys_org_closure c ON c.ancestor_id=d.id AND c.descendant_id=d.id WHERE d.status=1 AND c.depth IS NULL",
     ["sys_dept.id", "sys_dept.status", "sys_org_closure.ancestor_id", "sys_org_closure.descendant_id", "sys_org_closure.depth"], False),
    ("ORG-03", "组织", "P1", "组织闭包指向不存在部门",
     "SELECT 'ORG-03',COUNT(*) FROM sys_org_closure c LEFT JOIN sys_dept a ON c.ancestor_id=a.id LEFT JOIN sys_dept d ON c.descendant_id=d.id WHERE a.id IS NULL OR d.id IS NULL",
     ["sys_org_closure.ancestor_id", "sys_org_closure.descendant_id", "sys_dept.id"], False),
    ("ORG-04", "组织", "P2", "部门负责人指向不存在用户",
     "SELECT 'ORG-04',COUNT(*) FROM sys_dept d LEFT JOIN sys_user u ON d.leader_id=u.id WHERE d.leader_id IS NOT NULL AND u.id IS NULL",
     ["sys_dept.leader_id", "sys_user.id"], False),
    ("ORG-05", "组织", "P1", "组织管理员指向不存在组织/用户",
     "SELECT 'ORG-05',COUNT(*) FROM sys_org_admin oa LEFT JOIN sys_dept d ON oa.org_id=d.id LEFT JOIN sys_user u ON oa.user_id=u.id WHERE d.id IS NULL OR u.id IS NULL",
     ["sys_org_admin.org_id", "sys_org_admin.user_id", "sys_dept.id", "sys_user.id"], False),
    # 权限
    ("PERM-01", "权限", "P2", "范围行 ROLE 主体不存在（临时角色残留）",
     "SELECT 'PERM-01',COUNT(*) FROM role_perm_scope s LEFT JOIN sys_role r ON s.assignee_key=r.role_code WHERE s.assignee_type='ROLE' AND r.id IS NULL",
     ["role_perm_scope.assignee_type", "role_perm_scope.assignee_key", "sys_role.role_code"], False),
    ("PERM-02", "权限", "P1", "范围行 USER 主体不存在",
     "SELECT 'PERM-02',COUNT(*) FROM role_perm_scope s LEFT JOIN sys_user u ON u.id=CAST(s.assignee_key AS UNSIGNED) WHERE s.assignee_type='USER' AND u.id IS NULL",
     ["role_perm_scope.assignee_type", "role_perm_scope.assignee_key", "sys_user.id"], False),
    ("PERM-03", "权限", "P1", "范围级别非法值",
     "SELECT 'PERM-03',COUNT(*) FROM role_perm_scope WHERE scope_level NOT IN ('all','custom','region','office','dept','org_and_children','org','team','self')",
     ["role_perm_scope.scope_level"], False),
    ("PERM-04", "权限", "P1", "范围锚点非法值",
     "SELECT 'PERM-04',COUNT(*) FROM role_perm_scope WHERE anchor NOT IN ('OWNER','ORG')",
     ["role_perm_scope.anchor"], False),
    ("PERM-05", "权限", "P1", "同一主体同一模块动作存在多套范围（歧义）",
     "SELECT 'PERM-05',COUNT(*) FROM (SELECT assignee_type,assignee_key,module,action FROM role_perm_scope GROUP BY assignee_type,assignee_key,module,action HAVING COUNT(DISTINCT scope_level)>1) t",
     ["role_perm_scope.assignee_type", "role_perm_scope.assignee_key", "role_perm_scope.module", "role_perm_scope.action", "role_perm_scope.scope_level"], False),
    ("PERM-06", "权限", "P2", "范围行主体键为空",
     "SELECT 'PERM-06',COUNT(*) FROM role_perm_scope WHERE assignee_key IS NULL OR assignee_key=''",
     ["role_perm_scope.assignee_key"], False),
    ("PERM-07", "权限", "P1", "组织范围明细指向不存在范围行/部门",
     "SELECT 'PERM-07',COUNT(*) FROM perm_scope_org ps LEFT JOIN role_perm_scope s ON ps.scope_id=s.id LEFT JOIN sys_dept d ON ps.org_id=d.id WHERE s.id IS NULL OR d.id IS NULL",
     ["perm_scope_org.scope_id", "perm_scope_org.org_id", "role_perm_scope.id", "sys_dept.id"], False),
    ("PERM-08", "权限", "P1", "字段范围明细指向不存在范围行",
     "SELECT 'PERM-08',COUNT(*) FROM perm_scope_field pf LEFT JOIN role_perm_scope s ON pf.scope_id=s.id WHERE s.id IS NULL",
     ["perm_scope_field.scope_id", "role_perm_scope.id"], False),
    ("PERM-09", "权限", "P1", "角色-菜单绑定孤儿",
     "SELECT 'PERM-09',COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_role r ON rm.role_id=r.id LEFT JOIN sys_menu m ON rm.menu_id=m.id WHERE r.id IS NULL OR m.id IS NULL",
     ["sys_role_menu.role_id", "sys_role_menu.menu_id", "sys_role.id", "sys_menu.id"], False),
    # 客户
    ("CUS-01", "客户", "P1", "客户负责人不存在",
     "SELECT 'CUS-01',COUNT(*) FROM biz_customer c LEFT JOIN sys_user u ON c.owner_id=u.id WHERE c.deleted=0 AND c.owner_id IS NOT NULL AND u.id IS NULL",
     ["biz_customer.owner_id", "biz_customer.deleted", "sys_user.id"], False),
    ("CUS-02", "客户", "P1", "客户部门不存在",
     "SELECT 'CUS-02',COUNT(*) FROM biz_customer c LEFT JOIN sys_dept d ON c.dept_id=d.id WHERE c.deleted=0 AND c.dept_id IS NOT NULL AND d.id IS NULL",
     ["biz_customer.dept_id", "biz_customer.deleted", "sys_dept.id"], False),
    ("CUS-03", "客户", "P1", "活跃客户编码重复",
     "SELECT 'CUS-03',COUNT(*) FROM (SELECT customer_code FROM biz_customer WHERE deleted=0 GROUP BY customer_code HAVING COUNT(*)>1) t",
     ["biz_customer.customer_code", "biz_customer.deleted"], False),
    ("CUS-04", "客户", "P2", "活跃客户名称重复（迁移前存量，需 grandfather 核对）",
     "SELECT 'CUS-04',COUNT(*) FROM (SELECT customer_name FROM biz_customer WHERE deleted=0 GROUP BY customer_name HAVING COUNT(*)>1) t",
     ["biz_customer.customer_name", "biz_customer.deleted"], True),
    ("CUS-05", "客户", "P1", "客户状态非法值",
     "SELECT 'CUS-05',COUNT(*) FROM biz_customer WHERE status NOT IN ('normal','warning','sea','archived')",
     ["biz_customer.status"], False),
    ("CUS-06", "客户", "P2", "到期日早于注册日",
     "SELECT 'CUS-06',COUNT(*) FROM biz_customer WHERE deleted=0 AND register_date IS NOT NULL AND expire_date IS NOT NULL AND expire_date<register_date",
     ["biz_customer.register_date", "biz_customer.expire_date", "biz_customer.deleted"], False),
    ("CUS-07", "客户", "P1", "联系人孤儿",
     "SELECT 'CUS-07',COUNT(*) FROM biz_contact ct LEFT JOIN biz_customer c ON ct.customer_id=c.id WHERE ct.deleted=0 AND c.id IS NULL",
     ["biz_contact.customer_id", "biz_contact.deleted", "biz_customer.id"], False),
    ("CUS-08", "客户", "P1", "分享记录孤儿或重复",
     "SELECT 'CUS-08',COUNT(*) FROM biz_customer_share s LEFT JOIN biz_customer c ON s.customer_id=c.id LEFT JOIN sys_user u ON s.shared_to_user_id=u.id WHERE c.id IS NULL OR u.id IS NULL",
     ["biz_customer_share.customer_id", "biz_customer_share.shared_to_user_id", "biz_customer.id", "sys_user.id"], False),
    ("CUS-09", "客户", "P1", "转移记录用户不存在",
     "SELECT 'CUS-09',COUNT(*) FROM biz_customer_transfer t LEFT JOIN sys_user a ON t.from_user_id=a.id LEFT JOIN sys_user b ON t.to_user_id=b.id WHERE a.id IS NULL OR b.id IS NULL",
     ["biz_customer_transfer.from_user_id", "biz_customer_transfer.to_user_id", "sys_user.id"], False),
    ("CUS-10", "客户", "P1", "阶段金额孤儿或负数",
     "SELECT 'CUS-10',COUNT(*) FROM biz_customer_stage_amount a LEFT JOIN biz_customer c ON a.customer_id=c.id WHERE a.deleted=0 AND (c.id IS NULL OR a.invoice_amount<0 OR a.payment_amount<0)",
     ["biz_customer_stage_amount.customer_id", "biz_customer_stage_amount.deleted", "biz_customer_stage_amount.invoice_amount", "biz_customer_stage_amount.payment_amount", "biz_customer.id"], False),
    ("CUS-11", "客户", "P1", "开票孤儿/负数/作废无原因",
     "SELECT 'CUS-11',COUNT(*) FROM biz_invoice i LEFT JOIN biz_customer c ON i.customer_id=c.id WHERE i.deleted=0 AND (c.id IS NULL OR i.amount<0 OR (i.status='voided' AND (i.voided_reason IS NULL OR i.voided_reason='')))",
     ["biz_invoice.customer_id", "biz_invoice.deleted", "biz_invoice.amount", "biz_invoice.status", "biz_invoice.voided_reason", "biz_customer.id"], False),
    ("CUS-12", "客户", "P1", "回款孤儿或负数",
     "SELECT 'CUS-12',COUNT(*) FROM biz_payment p LEFT JOIN biz_customer c ON p.customer_id=c.id WHERE p.deleted=0 AND (c.id IS NULL OR p.payment_amount<0)",
     ["biz_payment.customer_id", "biz_payment.deleted", "biz_payment.payment_amount", "biz_customer.id"], False),
    ("CUS-13", "客户", "P2", "客户事件/日志孤儿",
     "SELECT 'CUS-13',COUNT(*) FROM biz_customer_event e LEFT JOIN biz_customer c ON e.customer_id=c.id WHERE c.id IS NULL",
     ["biz_customer_event.customer_id", "biz_customer.id"], False),
    ("CUS-14", "客户", "P1", "客户指标孤儿或重复",
     "SELECT 'CUS-14',COUNT(*) FROM biz_customer_indicator i LEFT JOIN biz_customer c ON i.customer_id=c.id WHERE c.id IS NULL OR i.customer_id IN (SELECT customer_id FROM biz_customer_indicator GROUP BY customer_id HAVING COUNT(*)>1)",
     ["biz_customer_indicator.customer_id", "biz_customer.id"], False),
    ("CUS-15", "客户", "P1", "公海客户无任何池记录",
     "SELECT 'CUS-15',COUNT(*) FROM biz_customer c LEFT JOIN biz_sea_pool p ON p.customer_id=c.id WHERE c.deleted=0 AND c.status='sea' AND p.id IS NULL",
     ["biz_customer.status", "biz_customer.deleted", "biz_sea_pool.customer_id", "biz_sea_pool.active_flag"], False),
    ("CUS-16", "客户", "P1", "活跃客户负责人为空",
     "SELECT 'CUS-16',COUNT(*) FROM biz_customer WHERE deleted=0 AND owner_id IS NULL",
     ["biz_customer.owner_id", "biz_customer.deleted"], False),
    # 公海
    ("SEA-01", "公海", "P1", "池记录引用不存在池配置",
     "SELECT 'SEA-01',COUNT(*) FROM biz_sea_pool p LEFT JOIN biz_sea_config cfg ON p.pool_id=cfg.id WHERE p.pool_id IS NOT NULL AND cfg.id IS NULL",
     ["biz_sea_pool.pool_id", "biz_sea_config.id"], False),
    ("SEA-02", "公海", "P1", "池权限范围引用不存在配置",
     "SELECT 'SEA-02',COUNT(*) FROM biz_sea_config_scope s LEFT JOIN biz_sea_config c ON s.config_id=c.id WHERE c.id IS NULL",
     ["biz_sea_config_scope.config_id", "biz_sea_config.id"], False),
    ("SEA-03", "公海", "P1", "池区域引用不存在配置/部门",
     "SELECT 'SEA-03',COUNT(*) FROM biz_sea_config_region r LEFT JOIN biz_sea_config c ON r.config_id=c.id LEFT JOIN sys_dept d ON r.region_id=d.id WHERE c.id IS NULL OR d.id IS NULL",
     ["biz_sea_config_region.config_id", "biz_sea_config_region.region_id", "biz_sea_config.id", "sys_dept.id"], False),
    ("SEA-04", "公海", "P2", "同一客户存在多条 available 池记录",
     "SELECT 'SEA-04',COUNT(*) FROM (SELECT customer_id FROM biz_sea_pool WHERE status='available' GROUP BY customer_id HAVING COUNT(*)>1) t",
     ["biz_sea_pool.customer_id", "biz_sea_pool.status"], False),
    # 审批
    ("APP-01", "审批", "P1", "审批单客户不存在",
     "SELECT 'APP-01',COUNT(*) FROM biz_approval a LEFT JOIN biz_customer c ON a.customer_id=c.id WHERE a.customer_id IS NOT NULL AND c.id IS NULL",
     ["biz_approval.customer_id", "biz_customer.id"], False),
    ("APP-02", "审批", "P1", "审批节点孤儿",
     "SELECT 'APP-02',COUNT(*) FROM biz_approval_node n LEFT JOIN biz_approval a ON n.approval_id=a.id WHERE a.id IS NULL",
     ["biz_approval_node.approval_id", "biz_approval.id"], False),
    ("APP-03", "审批", "P1", "审批节点审批人不存在",
     "SELECT 'APP-03',COUNT(*) FROM biz_approval_node n LEFT JOIN sys_user u ON n.approver_id=u.id WHERE n.approver_id IS NOT NULL AND u.id IS NULL",
     ["biz_approval_node.approver_id", "sys_user.id"], False),
    ("APP-04", "审批", "P1", "审批状态非法值",
     "SELECT 'APP-04',COUNT(*) FROM biz_approval WHERE status NOT IN ('pending','reviewing','approved','rejected','cancelled')",
     ["biz_approval.status"], False),
    ("APP-05", "审批", "P1", "已通过审批无通过节点",
     "SELECT 'APP-05',COUNT(*) FROM biz_approval a WHERE a.status='approved' AND NOT EXISTS (SELECT 1 FROM biz_approval_node n WHERE n.approval_id=a.id AND n.action='approve')",
     ["biz_approval.status", "biz_approval_node.approval_id", "biz_approval_node.action"], False),
    ("APP-06", "审批", "P2", "申请人自审自批样本（需策略确认）",
     "SELECT 'APP-06',COUNT(*) FROM biz_approval a JOIN biz_approval_node n ON n.approval_id=a.id WHERE a.applicant_id=n.approver_id",
     ["biz_approval.applicant_id", "biz_approval_node.approval_id", "biz_approval_node.approver_id"], True),
    ("APP-07", "审批", "P1", "审批节点重复（同单同人同序）",
     "SELECT 'APP-07',COUNT(*) FROM (SELECT approval_id,approver_id,node_order FROM biz_approval_node GROUP BY approval_id,approver_id,node_order HAVING COUNT(*)>1) t",
     ["biz_approval_node.approval_id", "biz_approval_node.approver_id", "biz_approval_node.node_order"], False),
    # 文件
    ("FIL-01", "文件", "P1", "文件引用不存在文件夹",
     "SELECT 'FIL-01',COUNT(*) FROM biz_file f LEFT JOIN biz_file_folder d ON f.folder_id=d.id WHERE f.deleted=0 AND f.folder_id IS NOT NULL AND d.id IS NULL",
     ["biz_file.folder_id", "biz_file.deleted", "biz_file_folder.id"], False),
    ("FIL-02", "文件", "P1", "文件夹父节点不存在（非根）",
     "SELECT 'FIL-02',COUNT(*) FROM biz_file_folder f LEFT JOIN biz_file_folder p ON f.parent_id=p.id WHERE f.deleted=0 AND f.parent_id<>0 AND p.id IS NULL",
     ["biz_file_folder.parent_id", "biz_file_folder.deleted", "biz_file_folder.id"], False),
    ("FIL-03", "文件", "P1", "文件夹自指父节点",
     "SELECT 'FIL-03',COUNT(*) FROM biz_file_folder WHERE deleted=0 AND parent_id=id",
     ["biz_file_folder.parent_id", "biz_file_folder.deleted"], False),
    ("FIL-04", "文件", "P1", "文件版本孤儿或版本号重复",
     "SELECT 'FIL-04',COUNT(*) FROM biz_file_version v LEFT JOIN biz_file f ON v.file_id=f.id WHERE f.id IS NULL OR v.file_id IN (SELECT file_id FROM biz_file_version GROUP BY file_id,version_no HAVING COUNT(*)>1)",
     ["biz_file_version.file_id", "biz_file_version.version_no", "biz_file.id"], False),
    ("FIL-05", "文件", "P1", "文件分享孤儿或重复",
     "SELECT 'FIL-05',COUNT(*) FROM biz_file_share s LEFT JOIN biz_file f ON s.file_id=f.id LEFT JOIN sys_user u ON s.shared_to_user_id=u.id WHERE f.id IS NULL OR u.id IS NULL",
     ["biz_file_share.file_id", "biz_file_share.shared_to_user_id", "biz_file.id", "sys_user.id"], False),
    ("FIL-06", "文件", "P1", "文件夹 ACL 孤儿",
     "SELECT 'FIL-06',COUNT(*) FROM biz_folder_acl a LEFT JOIN biz_file_folder f ON a.folder_id=f.id LEFT JOIN sys_role r ON a.role_id=r.id WHERE f.id IS NULL OR r.id IS NULL",
     ["biz_folder_acl.folder_id", "biz_folder_acl.role_id", "biz_file_folder.id", "sys_role.id"], False),
    ("FIL-07", "文件", "P1", "文件上传人不存在",
     "SELECT 'FIL-07',COUNT(*) FROM biz_file f LEFT JOIN sys_user u ON f.uploader_id=u.id WHERE f.deleted=0 AND u.id IS NULL",
     ["biz_file.uploader_id", "biz_file.deleted", "sys_user.id"], False),
    ("FIL-08", "文件", "P1", "文件 scope 非法值",
     "SELECT 'FIL-08',COUNT(*) FROM biz_file WHERE deleted=0 AND file_scope NOT IN ('private','public','incentive','fabushi','customer')",
     ["biz_file.file_scope", "biz_file.deleted"], False),
    ("FIL-09", "文件", "P2", "文件大小非正",
     "SELECT 'FIL-09',COUNT(*) FROM biz_file WHERE deleted=0 AND (file_size IS NULL OR file_size<=0)",
     ["biz_file.file_size", "biz_file.deleted"], False),
    ("FIL-10", "文件", "P2", "活跃文件 oss_key 重复",
     "SELECT 'FIL-10',COUNT(*) FROM (SELECT oss_key FROM biz_file WHERE deleted=0 GROUP BY oss_key HAVING COUNT(*)>1) t",
     ["biz_file.oss_key", "biz_file.deleted"], False),
    ("FIL-11", "文件", "P1", "类型默认授权孤儿文件夹（仅非空 folder_id）",
     "SELECT 'FIL-11',COUNT(*) FROM biz_folder_type_default d LEFT JOIN biz_file_folder f ON d.folder_id=f.id WHERE d.folder_id IS NOT NULL AND d.folder_id<>0 AND f.id IS NULL",
     ["biz_folder_type_default.folder_id", "biz_file_folder.id"], False),
    # 激励
    ("INC-01", "激励", "P1", "激励记录客户不存在",
     "SELECT 'INC-01',COUNT(*) FROM biz_incentive_record r LEFT JOIN biz_customer c ON r.customer_id=c.id WHERE r.customer_id IS NOT NULL AND c.id IS NULL",
     ["biz_incentive_record.customer_id", "biz_customer.id"], False),
    ("INC-02", "激励", "P1", "激励记录用户不存在",
     "SELECT 'INC-02',COUNT(*) FROM biz_incentive_record r LEFT JOIN sys_user u ON r.user_id=u.id WHERE u.id IS NULL",
     ["biz_incentive_record.user_id", "sys_user.id"], False),
    ("INC-03", "激励", "P1", "激励主文件引用不存在（file_id）",
     "SELECT 'INC-03',COUNT(*) FROM biz_incentive_record r WHERE r.file_id IS NOT NULL AND r.file_id NOT IN (SELECT id FROM biz_file)",
     ["biz_incentive_record.file_id", "biz_file.id"], False),
    ("INC-04", "激励", "P1", "激励类别孤儿",
     "SELECT 'INC-04',COUNT(*) FROM biz_incentive_record r LEFT JOIN biz_incentive_category c ON r.category_id=c.id WHERE r.category_id IS NOT NULL AND c.id IS NULL",
     ["biz_incentive_record.category_id", "biz_incentive_category.id"], False),
    ("INC-05", "激励", "P2", "激励记录业务键重复（需业务确认）",
     "SELECT 'INC-05',COUNT(*) FROM (SELECT user_id,category_id,customer_id,period FROM biz_incentive_record GROUP BY user_id,category_id,customer_id,period HAVING COUNT(*)>1) t",
     ["biz_incentive_record.user_id", "biz_incentive_record.category_id", "biz_incentive_record.customer_id", "biz_incentive_record.period"], True),
    ("INC-06", "激励", "P1", "激励周期为空",
     "SELECT 'INC-06',COUNT(*) FROM biz_incentive_record WHERE period IS NULL OR period=''",
     ["biz_incentive_record.period"], False),
    ("INC-07", "激励", "P1", "激励分数为负",
     "SELECT 'INC-07',COUNT(*) FROM biz_incentive_record WHERE score<0",
     ["biz_incentive_record.score"], False),
    ("INC-08", "激励", "P1", "已读记录孤儿",
     "SELECT 'INC-08',COUNT(*) FROM biz_incentive_read r LEFT JOIN biz_incentive_record x ON r.record_id=x.id LEFT JOIN sys_user u ON r.user_id=u.id WHERE x.id IS NULL OR u.id IS NULL",
     ["biz_incentive_read.record_id", "biz_incentive_read.user_id", "biz_incentive_record.id", "sys_user.id"], False),
    ("INC-09", "激励", "P1", "激励授权指向不存在用户",
     "SELECT 'INC-09',COUNT(*) FROM biz_incentive_grant g LEFT JOIN sys_user u ON g.user_id=u.id WHERE u.id IS NULL",
     ["biz_incentive_grant.user_id", "sys_user.id"], False),
    # 积分
    ("SCO-01", "积分", "P1", "积分条目孤儿",
     "SELECT 'SCO-01',COUNT(*) FROM biz_score_entry e LEFT JOIN biz_score_item i ON e.item_id=i.id LEFT JOIN sys_user u ON e.user_id=u.id WHERE e.deleted=0 AND (i.id IS NULL OR u.id IS NULL)",
     ["biz_score_entry.item_id", "biz_score_entry.user_id", "biz_score_entry.deleted", "biz_score_item.id", "sys_user.id"], False),
    ("SCO-02", "积分", "P2", "积分条目重复（需业务确认）",
     "SELECT 'SCO-02',COUNT(*) FROM (SELECT period,item_id,user_id FROM biz_score_entry WHERE deleted=0 GROUP BY period,item_id,user_id HAVING COUNT(*)>1) t",
     ["biz_score_entry.period", "biz_score_entry.item_id", "biz_score_entry.user_id", "biz_score_entry.deleted"], True),
    ("SCO-03", "积分", "P1", "积分参与人孤儿或重复",
     "SELECT 'SCO-03',COUNT(*) FROM biz_score_participant p LEFT JOIN sys_user u ON p.user_id=u.id WHERE u.id IS NULL OR p.user_id IN (SELECT user_id FROM biz_score_participant GROUP BY period,user_id HAVING COUNT(*)>1)",
     ["biz_score_participant.user_id", "biz_score_participant.period", "sys_user.id"], False),
    ("SCO-04", "积分", "P1", "积分超出条目上下限",
     "SELECT 'SCO-04',COUNT(*) FROM biz_score_entry e JOIN biz_score_item i ON e.item_id=i.id WHERE e.deleted=0 AND ((i.score_min IS NOT NULL AND e.score<i.score_min) OR (i.score_max IS NOT NULL AND e.score>i.score_max))",
     ["biz_score_entry.score", "biz_score_entry.item_id", "biz_score_entry.deleted", "biz_score_item.score_min", "biz_score_item.score_max"], False),
    ("SCO-05", "积分", "P1", "积分条目分数为负",
     "SELECT 'SCO-05',COUNT(*) FROM biz_score_entry WHERE deleted=0 AND score<0",
     ["biz_score_entry.score", "biz_score_entry.deleted"], False),
    # 提醒
    ("REM-01", "提醒", "P1", "提醒负责人不存在",
     "SELECT 'REM-01',COUNT(*) FROM biz_reminder r LEFT JOIN sys_user u ON r.owner_id=u.id WHERE u.id IS NULL",
     ["biz_reminder.owner_id", "sys_user.id"], False),
    ("REM-02", "提醒", "P1", "提醒客户不存在",
     "SELECT 'REM-02',COUNT(*) FROM biz_reminder r LEFT JOIN biz_customer c ON r.customer_id=c.id WHERE r.customer_id IS NOT NULL AND c.id IS NULL",
     ["biz_reminder.customer_id", "biz_customer.id"], False),
    ("REM-03", "提醒", "P2", "同一客户/负责人存在多条待处理提醒",
     "SELECT 'REM-03',COUNT(*) FROM (SELECT customer_id,owner_id FROM biz_reminder WHERE status='pending' GROUP BY customer_id,owner_id HAVING COUNT(*)>1) t",
     ["biz_reminder.customer_id", "biz_reminder.owner_id", "biz_reminder.status"], False),
    ("REM-04", "提醒", "P1", "已处理提醒无处理时间",
     "SELECT 'REM-04',COUNT(*) FROM biz_reminder WHERE status='processed' AND processed_at IS NULL",
     ["biz_reminder.status", "biz_reminder.processed_at"], False),
    ("REM-05", "提醒", "P2", "overdue_days 为负（未到期天数，信息项）",
     "SELECT 'REM-05',COUNT(*) FROM biz_reminder WHERE overdue_days<0",
     ["biz_reminder.overdue_days"], True),
    # 任务
    ("TAS-01", "任务", "P1", "任务负责人/创建人/客户不存在",
     "SELECT 'TAS-01',COUNT(*) FROM biz_task t LEFT JOIN sys_user a ON t.assignee_id=a.id LEFT JOIN sys_user c ON t.created_by=c.id LEFT JOIN biz_customer cu ON t.customer_id=cu.id WHERE t.deleted=0 AND (a.id IS NULL OR c.id IS NULL OR (t.customer_id IS NOT NULL AND cu.id IS NULL))",
     ["biz_task.assignee_id", "biz_task.created_by", "biz_task.customer_id", "biz_task.deleted", "sys_user.id", "biz_customer.id"], False),
    ("TAS-02", "任务", "P1", "任务状态非法值",
     "SELECT 'TAS-02',COUNT(*) FROM biz_task WHERE deleted=0 AND status NOT IN ('pending','in_progress','completed','cancelled')",
     ["biz_task.status", "biz_task.deleted"], False),
    ("TAS-03", "任务", "P1", "已完成任务无完成时间",
     "SELECT 'TAS-03',COUNT(*) FROM biz_task WHERE deleted=0 AND status='completed' AND completed_at IS NULL",
     ["biz_task.status", "biz_task.completed_at", "biz_task.deleted"], False),
    # 草稿
    ("DRF-01", "表单草稿", "P1", "草稿用户不存在",
     "SELECT 'DRF-01',COUNT(*) FROM biz_form_draft d LEFT JOIN sys_user u ON d.user_id=u.id WHERE u.id IS NULL",
     ["biz_form_draft.user_id", "sys_user.id"], False),
    ("DRF-02", "表单草稿", "P2", "同一用户同类型草稿重复",
     "SELECT 'DRF-02',COUNT(*) FROM (SELECT user_id,draft_type FROM biz_form_draft GROUP BY user_id,draft_type HAVING COUNT(*)>1) t",
     ["biz_form_draft.user_id", "biz_form_draft.draft_type"], True),
    ("DRF-03", "表单草稿", "P2", "草稿版本号非正",
     "SELECT 'DRF-03',COUNT(*) FROM biz_form_draft WHERE version<=0",
     ["biz_form_draft.version"], False),
    # 法布施
    ("FAB-01", "法布施", "P2", "阅读进度书不存在",
     "SELECT 'FAB-01',COUNT(*) FROM biz_fabushi_reading_progress r LEFT JOIN biz_fabushi_book b ON r.book_id=b.id WHERE b.id IS NULL",
     ["biz_fabushi_reading_progress.book_id", "biz_fabushi_book.id"], False),
    ("FAB-02", "法布施", "P1", "内容书目/章节孤儿",
     "SELECT 'FAB-02',COUNT(*) FROM biz_fabushi_post p LEFT JOIN biz_fabushi_book b ON p.book_id=b.id LEFT JOIN biz_fabushi_section s ON p.section_id=s.id WHERE p.deleted=0 AND ((p.book_id IS NOT NULL AND b.id IS NULL) OR (p.section_id IS NOT NULL AND s.id IS NULL))",
     ["biz_fabushi_post.book_id", "biz_fabushi_post.section_id", "biz_fabushi_post.deleted", "biz_fabushi_book.id", "biz_fabushi_section.id"], False),
    ("FAB-03", "法布施", "P1", "内容附件文件不存在",
     "SELECT 'FAB-03',COUNT(*) FROM biz_fabushi_post_attachment a LEFT JOIN biz_file f ON a.file_id=f.id WHERE f.id IS NULL",
     ["biz_fabushi_post_attachment.file_id", "biz_file.id"], False),
    ("FAB-04", "法布施", "P1", "评论主题/父评论孤儿",
     "SELECT 'FAB-04',COUNT(*) FROM biz_fabushi_comment c LEFT JOIN biz_fabushi_post p ON c.post_id=p.id LEFT JOIN biz_fabushi_comment pc ON c.parent_comment_id=pc.id WHERE c.deleted=0 AND (p.id IS NULL OR (c.parent_comment_id IS NOT NULL AND pc.id IS NULL))",
     ["biz_fabushi_comment.post_id", "biz_fabushi_comment.parent_comment_id", "biz_fabushi_comment.deleted", "biz_fabushi_post.id"], False),
    ("FAB-05", "法布施", "P1", "活动参与孤儿/重复",
     "SELECT 'FAB-05',COUNT(*) FROM biz_fabushi_activity_join j LEFT JOIN biz_fabushi_activity a ON j.activity_id=a.id LEFT JOIN sys_user u ON j.user_id=u.id WHERE a.id IS NULL OR u.id IS NULL OR j.activity_id IN (SELECT activity_id FROM biz_fabushi_activity_join GROUP BY activity_id,user_id HAVING COUNT(*)>1)",
     ["biz_fabushi_activity_join.activity_id", "biz_fabushi_activity_join.user_id", "biz_fabushi_activity.id", "sys_user.id"], False),
    ("FAB-06", "法布施", "P2", "点赞/已读/书架/进度重复",
     "SELECT 'FAB-06',COUNT(*) FROM (SELECT post_id,user_id FROM biz_fabushi_like GROUP BY post_id,user_id HAVING COUNT(*)>1 UNION ALL SELECT post_id,user_id FROM biz_fabushi_read_status GROUP BY post_id,user_id HAVING COUNT(*)>1 UNION ALL SELECT book_id,user_id FROM biz_fabushi_shelf GROUP BY book_id,user_id HAVING COUNT(*)>1 UNION ALL SELECT book_id,user_id FROM biz_fabushi_reading_progress GROUP BY book_id,user_id HAVING COUNT(*)>1) t",
     ["biz_fabushi_like.post_id", "biz_fabushi_like.user_id", "biz_fabushi_read_status.post_id", "biz_fabushi_read_status.user_id", "biz_fabushi_shelf.book_id", "biz_fabushi_shelf.user_id", "biz_fabushi_reading_progress.book_id", "biz_fabushi_reading_progress.user_id"], False),
    ("FAB-07", "法布施", "P1", "章节自指父节点",
     "SELECT 'FAB-07',COUNT(*) FROM biz_fabushi_section WHERE deleted=0 AND parent_id=id",
     ["biz_fabushi_section.parent_id", "biz_fabushi_section.deleted"], False),
    ("FAB-08", "法布施", "P1", "书目/内容/活动状态非法值",
     "SELECT 'FAB-08',COUNT(*) FROM biz_fabushi_book WHERE deleted=0 AND status NOT IN ('pending','approved','rejected','disabled')",
     ["biz_fabushi_book.status", "biz_fabushi_book.deleted"], False),
    ("FAB-09", "法布施", "P1", "内容状态非法值",
     "SELECT 'FAB-09',COUNT(*) FROM biz_fabushi_post WHERE deleted=0 AND status NOT IN ('published','offline')",
     ["biz_fabushi_post.status", "biz_fabushi_post.deleted"], False),
    ("FAB-10", "法布施", "P1", "活动状态非法值",
     "SELECT 'FAB-10',COUNT(*) FROM biz_fabushi_activity WHERE deleted=0 AND status NOT IN ('active','ended')",
     ["biz_fabushi_activity.status", "biz_fabushi_activity.deleted"], False),
    ("FAB-11", "法布施", "P1", "阅读进度页码越界",
     "SELECT 'FAB-11',COUNT(*) FROM biz_fabushi_reading_progress WHERE last_page>total_pages OR total_pages<=0",
     ["biz_fabushi_reading_progress.last_page", "biz_fabushi_reading_progress.total_pages"], False),
    # 统计对账（信息项）
    ("STA-01", "统计", "P2", "全量可见客户数（admin 口径，应 294）",
     "SELECT 'STA-01',COUNT(*) FROM biz_customer WHERE deleted=0 AND status NOT IN ('sea','archived')",
     ["biz_customer.deleted", "biz_customer.status"], True),
    ("STA-02", "统计", "P2", "开票金额合计与回款金额合计（信息）",
     "SELECT 'STA-02',ROUND(COALESCE((SELECT SUM(amount) FROM biz_invoice WHERE deleted=0),0),2)",
     ["biz_invoice.amount", "biz_invoice.deleted"], True),
]

# ---------- run ----------
passed, findings, missing, errors, info_rows = [], [], [], [], []
sql_lines = []
line_map = {}
for chk in CHECKS:
    cid, mod, sev, desc, sql, req, is_info = chk
    ok, miss = has(req)
    if not ok:
        missing.append((cid, mod, sev, desc, miss))
        continue
    line_map[len(sql_lines) + 1] = (cid, mod, sev, desc, is_info)
    sql_lines.append(sql + ";")
sql_script = "\n".join(sql_lines) + "\n"

cmd = ("ssh aigicrm-test 'set -a; . ~/aigicrm/.env; "
       "docker exec -i -e MYSQL_PWD=\"$DB_PASSWORD\" shared-mysql "
       "mysql -uroot \"$DB_NAME\" -N --default-character-set=utf8mb4 --force'")
proc = subprocess.run(cmd, shell=True, input=sql_script, text=True,
                      capture_output=True, timeout=600)
out = proc.stdout
err = proc.stderr

# map errors to lines
err_lines = {}
for m in re.finditer(r"at line (\d+)", err):
    err_lines[int(m.group(1))] = True

results = {}
for line in out.splitlines():
    f = line.split("\t")
    if len(f) >= 2 and f[0] in {c[0] for c in CHECKS}:
        results[f[0]] = f[1:]

for ln, (cid, mod, sev, desc, is_info) in sorted(line_map.items()):
    if cid not in results:
        status = "SQL_ERROR" if ln in err_lines else "NO_RESULT"
        errors.append((cid, mod, sev, desc, status))
        continue
    vals = results[cid]
    val = vals[0]
    sample = vals[1] if len(vals) > 1 else ""
    if is_info:
        info_rows.append((cid, mod, sev, desc, val, sample))
    elif val == "0":
        passed.append((cid, mod, sev, desc))
    else:
        findings.append((cid, mod, sev, desc, val, sample))

# ---------- Python JSON-aware 复核：激励附件 JSON 数组引用 ----------
try:
    file_ids = {int(x.strip()) for x in open(os.path.join(DATA, "file_ids.tsv"), encoding="utf-8") if x.strip()}
    bad_records = []
    for line in open(os.path.join(DATA, "inc_file_refs.tsv"), encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) < 6:
            continue
        rid = int(f[0])
        vals = []
        for cell in f[1:]:
            cell = cell.strip()
            if not cell:
                continue
            try:
                if cell.startswith("["):
                    vals += [int(x) for x in json.loads(cell)]
                else:
                    vals.append(int(cell))
            except Exception:
                pass
        miss = [x for x in vals if x not in file_ids]
        if miss:
            bad_records.append((rid, miss))
    if bad_records:
        findings.append(("INC-03P", "激励", "P1", "激励附件 JSON 引用不存在（Python 复核）",
                         str(len(bad_records)), ",".join(str(x[0]) for x in bad_records[:10])))
    else:
        passed.append(("INC-03P", "激励", "P2", "激励附件 JSON 引用不存在（Python 复核）"))
except Exception as e:
    errors.append(("INC-03P", "激励", "P2", "激励附件 JSON 复核失败", "ERROR:" + type(e).__name__))

# ---------- write ----------
tsv = os.path.join(EVID, "sql-only-checks.tsv")
with open(tsv, "w", encoding="utf-8") as f:
    f.write("id\tmodule\tseverity\tdesc\tvalue\tsample\tstatus\n")
    for x in passed:
        f.write(f"{x[0]}\t{x[1]}\t{x[2]}\t{x[3]}\t0\t\tPASS\n")
    for x in findings:
        f.write(f"{x[0]}\t{x[1]}\t{x[2]}\t{x[3]}\t{x[4]}\t{x[5]}\tFINDING\n")
    for x in info_rows:
        f.write(f"{x[0]}\t{x[1]}\t{x[2]}\t{x[3]}\t{x[4]}\t{x[5]}\tINFO\n")
    for x in missing:
        f.write(f"{x[0]}\t{x[1]}\t{x[2]}\t{x[3]}\t\t{x[4]}\tSCHEMA_MISSING\n")
    for x in errors:
        f.write(f"{x[0]}\t{x[1]}\t{x[2]}\t{x[3]}\t\t\t{x[4]}\n")

md = os.path.join(EVID, "sql-only-report.md")
with open(md, "w", encoding="utf-8") as f:
    f.write("# AIGICRM SQL-only 全模块完整性扫描\n\n")
    f.write(f"- 生成：{__import__('time').strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write("- 数据源：测试库只读；生产零写入；不调用 API\n")
    f.write(f"- 检查项：{len(CHECKS) + 1}；通过 {len(passed)}；发现 {len(findings)}；"
            f"缺 schema {len(missing)}；SQL 错误 {len(errors)}；信息 {len(info_rows)}\n\n")
    f.write("## 发现（按模块）\n\n")
    cur = None
    for x in findings:
        if x[1] != cur:
            cur = x[1]; f.write(f"### {cur}\n\n")
        f.write(f"- **{x[0]}（{x[2]}）{x[3]}**：{x[4]}" + (f"；样本 {x[5]}" if x[5] else "") + "\n")
    if not findings:
        f.write("无。\n")
    f.write("\n## Schema 缺失（迁移未应用）\n\n")
    for x in missing:
        f.write(f"- {x[0]}（{x[1]}，{x[2]}）{x[3]}：缺 {x[4]}\n")
    if not missing:
        f.write("无。\n")
    f.write("\n## SQL 错误/无结果\n\n")
    for x in errors:
        f.write(f"- {x[0]}（{x[1]}，{x[2]}）{x[3]}：{x[4]}\n")
    if not errors:
        f.write("无。\n")
    f.write("\n## 信息项\n\n")
    for x in info_rows:
        f.write(f"- {x[0]}（{x[1]}）{x[3]}：{x[4]}\n")
    f.write("\n## 全部通过项\n\n")
    f.write(", ".join(x[0] for x in passed) + "\n")

print(json.dumps({
    "checks": len(CHECKS) + 1, "pass": len(passed), "findings": len(findings),
    "schema_missing": len(missing), "sql_errors": len(errors), "info": len(info_rows),
}, ensure_ascii=False))
for x in findings:
    print("FINDING", x[0], x[1], x[2], x[4], x[3], sep="\t")
for x in missing:
    print("SCHEMA_MISSING", x[0], x[4], sep="\t")
for x in errors:
    print("ERROR", x[0], x[4], sep="\t")
