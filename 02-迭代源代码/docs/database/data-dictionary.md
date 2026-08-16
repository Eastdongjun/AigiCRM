# YCMN 数据字典(自动生成)

> 生成时间: 2026-08-16 | 来源: 生产 39.99.35.218 MySQL information_schema
> 共 55 张表。本文件由 scripts/gen-data-dictionary.sh 自动生成,**禁止手改**;库结构变更后重新运行脚本。

## biz_approval 审批记录

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `approval_code` | varchar(30) | NO | NULL | UNI | 审批编号(P2024-001) |
| `approval_type` | varchar(50) | NO | NULL | MUL | 类型: claim/transfer/extend/storage |
| `applicant_id` | bigint | NO | NULL | MUL | 申请人ID |
| `customer_id` | bigint | YES | NULL |  | 关联客户ID |
| `current_node` | varchar(100) | YES | NULL |  | 当前审批节点 |
| `action_after_approve` | varchar(200) | YES | NULL |  | 通过后动作描述 |
| `extra_data` | json | YES | NULL |  | 扩展数据 |
| `status` | varchar(20) | YES | pending | MUL | 状态: pending/reviewing/approved/rejected |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `applicant_dept_id` | bigint | YES | NULL |  | 提交时申请人部门快照(V10) |
| `applicant_office_id` | bigint | YES | NULL |  | 提交时申请人办事处快照(V10) |

## biz_approval_config 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `flow_name` | varchar(100) | NO | NULL |  | 流程名称 |
| `flow_type` | varchar(50) | NO | NULL |  | 流程类型 |
| `approver_type` | varchar(20) | YES | NULL |  | 审批人类型 USER/ROLE/DEPT |
| `approver_ids` | varchar(500) | YES | NULL |  | 审批人ID列表 JSON |
| `sign_mode` | varchar(20) | YES | NULL |  | 签署模式 ALL/ANY |
| `sort_order` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_approval_flow 审批流程定义

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `flow_type` | varchar(50) | NO | NULL |  | 类型: claim/transfer/extend/storage |
| `flow_name` | varchar(100) | YES | NULL |  | 流程名称 |
| `approver_role_id` | bigint | YES | NULL |  | 审批角色ID |
| `sort_order` | int | YES | 0 |  | 审批顺序(多级审批) |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_approval_node 审批节点记录

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `approval_id` | bigint | NO | NULL | MUL |  |
| `approver_id` | bigint | NO | NULL | MUL | 审批人ID |
| `node_order` | int | YES | NULL |  | 节点顺序 |
| `action` | varchar(20) | YES | NULL |  | 操作: approve/reject |
| `comment` | varchar(500) | YES | NULL |  | 审批意见 |
| `operated_at` | datetime | YES | NULL |  | 操作时间 |
| `status` | varchar(20) | YES | pending |  | 状态 |
| `config_id` | bigint | YES | NULL |  |  |
| `sign_mode` | varchar(20) | YES | NULL |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_contact 客户联系人

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `customer_id` | bigint | NO | NULL | MUL | 客户ID |
| `name` | varchar(50) | YES | NULL |  | 姓名 |
| `position` | varchar(50) | YES | NULL |  | 职位 |
| `phone` | varchar(30) | YES | NULL |  | 电话 |
| `remark` | varchar(200) | YES | NULL |  | 备注 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_customer 客户表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `customer_code` | varchar(20) | NO | NULL | UNI | 客户编号(C001) |
| `customer_name` | varchar(200) | NO | NULL |  | 订货客户全称 |
| `invoice_name` | varchar(200) | YES | NULL |  | 开票名称 |
| `industry` | varchar(50) | YES | NULL | MUL | 行业 |
| `dev_type` | varchar(50) | YES | NULL |  | 开发类型(成熟/成熟创新/突破) |
| `group_name` | varchar(200) | YES | NULL |  | 所属集团 |
| `branch_company` | varchar(200) | YES | NULL |  | 所属分公司 |
| `unit_name` | varchar(200) | YES | NULL |  | 所属单位 |
| `capacity` | varchar(1024) | YES | NULL |  | 产能(万吨) |
| `province` | varchar(50) | YES | NULL |  | 省 |
| `city` | varchar(50) | YES | NULL |  | 市 |
| `district` | varchar(50) | YES | NULL |  | 区/县 |
| `address` | varchar(500) | YES | NULL |  | 厂址 |
| `has_terminal` | tinyint | YES | 0 |  | 是否终端 0否 1是 |
| `register_date` | date | YES | NULL |  | 注册日 |
| `last_invoice_date` | date | YES | NULL |  | 最近开票日 |
| `expire_date` | date | YES | NULL | MUL | 到期日(360天) |
| `adjust_date` | date | YES | NULL |  | 调整日(180天) |
| `transfer_date` | date | YES | NULL |  | 转移日 |
| `owner_id` | bigint | YES | NULL | MUL | 第一负责人ID |
| `manager_id` | bigint | YES | NULL |  | 管理上级ID |
| `office_id` | bigint | YES | NULL | MUL | 所属办事处ID |
| `dept_id` | bigint | YES | NULL | MUL | 所属部门(任意层级) |
| `region_id` | bigint | YES | NULL | MUL | 所属大区ID(dept_type=2) |
| `remark` | text | YES | NULL |  | 备注 |
| `status` | varchar(20) | YES | normal | MUL | 状态: normal/warning/expired/sea |
| `protection_status` | varchar(20) | YES | active |  | 保护状态: active/expiring/expired |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `deleted` | tinyint | YES | 0 | MUL |  |
| `relationship_score` | int | YES | 0 |  |  |
| `is_core` | tinyint | YES | 0 |  |  |
| `tags` | varchar(500) | YES | NULL |  |  |
| `group_id` | bigint | YES | NULL |  |  |
| `legal_entity_id` | bigint | YES | NULL |  |  |

## biz_customer_indicator 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `customer_id` | bigint | NO | NULL | UNI |  |
| `indicator_02` | int | YES | 0 |  |  |
| `indicator_03` | int | YES | 0 |  |  |
| `indicator_04` | int | YES | 0 |  |  |
| `indicator_05` | int | YES | 0 |  |  |
| `indicator_06` | int | YES | 0 |  |  |
| `is_core` | int | YES | 0 |  |  |
| `relationship_score` | int | YES | 0 |  |  |
| `updated_by` | bigint | YES | NULL |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_customer_log 客户跟进日志

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `customer_id` | bigint | NO | NULL | MUL |  |
| `user_id` | bigint | NO | NULL |  | 记录人 |
| `log_type` | varchar(50) | YES | NULL |  | 类型(终端考察/技术交流/产品试点报告/其他) |
| `content` | text | NO | NULL |  | 跟进内容 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_customer_share 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `customer_id` | bigint | NO | NULL | MUL |  |
| `shared_to_user_id` | bigint | NO | NULL |  |  |
| `shared_by_user_id` | bigint | YES | NULL |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## biz_customer_transfer 客户转移记录(离职/手动)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `from_user_id` | bigint | NO | NULL | MUL | 转移源用户 |
| `to_user_id` | bigint | NO | NULL | MUL | 接收用户 |
| `transfer_type` | varchar(20) | NO | NULL |  | leave=离职转移 / manual=手动转移 |
| `customer_count` | int | NO | 0 |  | 转移客户数 |
| `operator_id` | bigint | NO | NULL |  | 操作人 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_file 文件表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `folder_id` | bigint | YES | NULL | MUL | 所属目录ID |
| `file_name` | varchar(200) | NO | NULL |  | 文件名 |
| `file_size` | bigint | YES | NULL |  | 文件大小(字节) |
| `file_type` | varchar(20) | YES | NULL |  | 文件类型 |
| `oss_key` | varchar(500) | NO | NULL |  | OSS对象Key |
| `oss_url` | varchar(1000) | YES | NULL |  | OSS访问URL |
| `file_scope` | varchar(20) | YES | private | MUL | 范围: public/private/incentive |
| `uploader_id` | bigint | NO | NULL | MUL | 上传者ID |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `version` | int | NO | 1 |  | 版本号,覆盖替换+1 |
| `replace_by` | bigint | YES | NULL |  | 最近替换人 |
| `replaced_at` | datetime | YES | NULL |  | 最近替换时间 |

## biz_file_folder 文件目录表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `parent_id` | bigint | YES | 0 | MUL | 父目录ID |
| `folder_name` | varchar(100) | NO | NULL |  | 目录名称 |
| `folder_type` | varchar(20) | YES | NULL |  | 类型: public/private/incentive |
| `owner_id` | bigint | YES | NULL | MUL | 所属用户ID(私人目录) |
| `sort_order` | int | YES | 0 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `downloadable` | tinyint | NO | 1 |  | 下载开关:1=允许下载,0=仅预览 |

## biz_file_share 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `file_id` | bigint | NO | NULL | MUL |  |
| `shared_to_user_id` | bigint | NO | NULL |  |  |
| `shared_by_user_id` | bigint | YES | NULL |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_folder_acl 文件夹-角色授权表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `folder_id` | bigint | NO | NULL | MUL | 文件夹ID |
| `role_id` | bigint | NO | NULL |  | 角色ID |
| `perms` | varchar(128) | NO | NULL |  | 权限列表,逗号分隔:view,upload,edit,delete,share,download |
| `created_by` | bigint | YES | NULL |  |  |
| `created_at` | datetime | YES | NULL |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_incentive_category 激励分类配置

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `parent_id` | bigint | YES | 0 |  | 父分类ID(0为四项大类) |
| `category_name` | varchar(100) | NO | NULL |  | 分类名称 |
| `category_code` | varchar(50) | YES | NULL |  | 分类编码 |
| `score` | int | YES | 0 |  | 分值 |
| `sort_order` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_incentive_grant 激励专项授权(用户级, 替代载体角色模式)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `user_id` | bigint | NO | NULL | PRI | 用户ID |
| `can_view_shared` | tinyint | NO | 1 |  | 可查看他人已分享记录(默认开; 无记录=开; 0=收窄) |
| `can_view_all` | tinyint | NO | 0 |  | 可查看全部未分享记录(白名单, 默认关) |
| `can_edit` | tinyint | NO | 0 |  | 可编辑他人记录(白名单, 默认关) |
| `can_delete` | tinyint | NO | 0 |  | 可删除他人记录(白名单, 默认关) |
| `updated_by` | bigint | YES | NULL |  | 最后授权操作人ID |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  | 最后授权时间 |

## biz_incentive_permission 激励权限表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `user_id` | bigint | NO | NULL | UNI |  |
| `can_edit` | tinyint | YES | 0 |  |  |
| `can_delete` | tinyint | YES | 0 |  |  |
| `can_view_all` | tinyint | NO | 0 |  | 看全部激励(含未分享)独立开关 |
| `can_view_shared` | tinyint | NO | 1 |  | 查看已分享(默认1) |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_incentive_record 激励上传记录

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `user_id` | bigint | NO | NULL | MUL | 用户ID |
| `category_id` | bigint | NO | NULL | MUL | 分类ID |
| `customer_id` | bigint | YES | NULL | MUL |  |
| `period` | varchar(20) | YES | NULL | MUL | 周期(如2026上半年) |
| `industry` | varchar(50) | YES | NULL |  | 行业(取字典 industry) |
| `related_products` | varchar(1000) | YES | NULL |  | 相关产品(多行文本) |
| `specific_product` | varchar(200) | YES | NULL |  |  |
| `file_id` | varchar(500) | YES | NULL |  |  |
| `pre_install_question` | text | YES | NULL |  | 安装前问题 |
| `file_pre_id` | varchar(500) | YES | NULL |  |  |
| `file_mid_id` | varchar(500) | YES | NULL |  |  |
| `file_post_id` | varchar(500) | YES | NULL |  |  |
| `file_track_id` | varchar(500) | YES | NULL |  |  |
| `remark` | varchar(500) | YES | NULL |  | 备注 |
| `score` | int | YES | 0 |  | 得分 |
| `shared` | tinyint | YES | 0 |  |  |
| `updated_by` | bigint | YES | NULL |  | 最后修改人ID |
| `status` | varchar(20) | YES | uploaded |  | 状态 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## biz_invoice 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `customer_id` | bigint | NO | NULL | MUL |  |
| `invoice_date` | date | YES | NULL |  |  |
| `amount` | decimal(12,2) | YES | NULL |  |  |
| `invoice_no` | varchar(100) | YES | NULL |  |  |
| `remark` | varchar(500) | YES | NULL |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `invoice_number` | varchar(100) | YES | NULL |  |  |
| `invoice_company` | varchar(200) | YES | NULL |  |  |
| `invoice_type` | varchar(50) | YES | NULL |  |  |
| `created_by` | bigint | YES | NULL |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_payment 回款记录

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `customer_id` | bigint | NO | NULL | MUL | 客户ID |
| `payment_date` | date | YES | NULL |  | 回款日期 |
| `payment_amount` | decimal(12,2) | YES | NULL |  | 回款金额(万元) |
| `payment_method` | varchar(50) | YES | NULL |  | 回款方式 |
| `remark` | varchar(500) | YES | NULL |  | 备注 |
| `created_by` | bigint | YES | NULL |  | 创建人 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_reminder 提醒记录表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `customer_id` | bigint | NO | NULL | MUL |  |
| `reminder_type` | varchar(50) | YES | NULL |  | 类型: invoice_overdue/expiring |
| `owner_id` | bigint | YES | NULL | MUL | 负责人ID |
| `office_id` | bigint | YES | NULL |  | 办事处ID |
| `overdue_days` | int | YES | NULL |  | 已超天数 |
| `status` | varchar(20) | YES | pending | MUL | 状态: pending/processed |
| `processed_at` | datetime | YES | NULL |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## biz_score_entry 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `period` | varchar(20) | NO | NULL | MUL |  |
| `item_id` | bigint | NO | NULL |  |  |
| `user_id` | bigint | NO | NULL | MUL |  |
| `score` | int | YES | 0 |  |  |
| `scorer_id` | bigint | YES | NULL |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_score_item 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `category` | varchar(100) | NO | NULL |  |  |
| `product` | varchar(200) | NO | NULL |  |  |
| `score_min` | int | YES | 0 |  |  |
| `score_max` | int | YES | 5 |  |  |
| `sort_order` | int | YES | 0 |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_score_participant 积分录入周期参与同事名单

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `period` | varchar(20) | NO | NULL | MUL | 周期，如 2026上半年 |
| `user_id` | bigint | NO | NULL |  | 参与同事的用户ID |
| `sort_order` | int | YES | 0 |  | 列顺序 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_sea_config 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `pool_name` | varchar(100) | YES | NULL |  |  |
| `included_offices` | varchar(500) | YES | NULL |  |  |
| `visible_roles` | varchar(500) | YES | NULL |  |  |
| `sort_order` | int | YES | 0 |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `region_ids` | varchar(500) | YES | NULL |  |  |
| `view_role_ids` | varchar(500) | YES | NULL |  |  |
| `visible_dept_ids` | varchar(500) | YES | NULL |  | 可见部门ID(逗号分隔,dept_type=4) |
| `visible_user_ids` | varchar(500) | YES | NULL |  | 可见人员ID(逗号分隔) |
| `status` | int | YES | 1 |  |  |
| `edit_dept_ids` | varchar(500) | YES | NULL |  | 编辑权限-授权组织 |
| `edit_user_ids` | varchar(500) | YES | NULL |  | 编辑权限-授权人员 |
| `delete_dept_ids` | varchar(500) | YES | NULL |  | 删除权限-授权组织 |
| `delete_user_ids` | varchar(500) | YES | NULL |  | 删除权限-授权人员 |
| `import_dept_ids` | varchar(500) | YES | NULL |  | 导入权限-授权组织 |
| `import_user_ids` | varchar(500) | YES | NULL |  | 导入权限-授权人员 |
| `claim_dept_ids` | varchar(500) | YES | NULL |  | 捞取权限-授权组织 |
| `claim_user_ids` | varchar(500) | YES | NULL |  | 捞取权限-授权人员 |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |

## biz_sea_config_region 公海池-办事处关联(V10，取代 biz_sea_config.region_ids 的 CSV 存储)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 |  | 集团ID |
| `config_id` | bigint | NO | NULL | MUL | 公海池配置ID → biz_sea_config.id |
| `region_id` | bigint | NO | NULL | MUL | 办事处/大区部门ID → sys_dept.id |

## biz_sea_config_scope 公海池权限主体(V11，取代 biz_sea_config 的 10 个 CSV 列)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 |  | 集团ID |
| `config_id` | bigint | NO | NULL | MUL | 公海池配置 → biz_sea_config.id |
| `perm_type` | varchar(10) | NO | NULL | MUL | 权限类型: VIEW/EDIT/DELETE/IMPORT/CLAIM |
| `target_type` | varchar(10) | NO | NULL |  | 主体类型: DEPT/USER |
| `target_id` | bigint | NO | NULL |  | DEPT→sys_dept.id / USER→sys_user.id |

## biz_sea_pool 公海池

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 | MUL | 集团ID |
| `customer_id` | bigint | NO | NULL | MUL | 客户ID |
| `original_owner_id` | bigint | YES | NULL |  | 原负责人ID |
| `enter_reason` | varchar(50) | YES | NULL |  | 进入原因: protection_expired/abandoned/resignation |
| `enter_date` | datetime | YES | NULL |  | 进入时间 |
| `claim_user_id` | bigint | YES | NULL |  | 捞取人ID |
| `claim_date` | datetime | YES | NULL |  | 捞取时间 |
| `status` | varchar(20) | YES | available | MUL | 状态: available/pending_approval/claimed |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `pool_id` | bigint | YES | NULL | MUL |  |
| `active_flag` | bigint | YES | NULL |  | 未捞取时=customer_id，已捞取时=NULL；仅供唯一索引使用(V10) |

## flyway_schema_history 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `installed_rank` | int | NO | NULL | PRI |  |
| `version` | varchar(50) | YES | NULL |  |  |
| `description` | varchar(200) | NO | NULL |  |  |
| `type` | varchar(20) | NO | NULL |  |  |
| `script` | varchar(1000) | NO | NULL |  |  |
| `checksum` | int | YES | NULL |  |  |
| `installed_by` | varchar(100) | NO | NULL |  |  |
| `installed_on` | timestamp | NO | CURRENT_TIMESTAMP |  |  |
| `execution_time` | int | NO | NULL |  |  |
| `success` | tinyint(1) | NO | NULL | MUL |  |

## perm_scope_field 字段级可见性(V11，建表留位；运行时接入待收敛切换后)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 |  |  |
| `scope_id` | bigint | NO | NULL | MUL | 所属范围配置 → role_perm_scope.id |
| `field_name` | varchar(64) | NO | NULL | MUL | 实体字段名(驼峰)，如 revenue / invoiceAmount |
| `visible` | tinyint | NO | 0 |  | 0=对该范围隐藏 1=显式可见 |

## perm_scope_org scope=custom 时的授权组织清单(V11，取代 sys_role_org/sys_user_org)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `tenant_id` | bigint | NO | 1 |  | 集团ID |
| `scope_id` | bigint | NO | NULL | MUL | 所属范围配置 → role_perm_scope.id |
| `org_id` | bigint | NO | NULL | MUL | 授权组织 → sys_dept.id |
| `include_children` | tinyint | NO | 1 |  | 是否含下级组织 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## perm_scope_shadow_diff 数据范围新旧模型影子比对结果(V11)；只有全量无差异才允许切换 ycmn.scope.use-unified=true

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `run_id` | varchar(40) | NO | NULL | MUL | 一次比对批次 |
| `user_id` | bigint | NO | NULL |  |  |
| `module` | varchar(50) | NO | NULL |  |  |
| `action` | varchar(50) | NO | NULL |  |  |
| `legacy_count` | int | NO | NULL |  | 旧模型解析出的可见主体数 |
| `new_count` | int | NO | NULL |  | 新模型解析出的可见主体数 |
| `only_legacy` | text | YES | NULL |  | 仅旧模型可见(切换后会丢可见性→业务停摆) |
| `only_new` | text | YES | NULL |  | 仅新模型可见(切换后会多可见性→数据泄露) |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## role_perm_scope 角色-模块-动作 数据范围

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `role_code` | varchar(50) | NO | NULL |  | 角色编码(sys_role.role_code) |
| `module` | varchar(50) | NO | NULL |  | 模块；* = 通配所有模块(用于承载角色/用户的全局默认范围) |
| `action` | varchar(50) | NO | NULL |  | 动作；* = 通配所有动作。解析优先级: (m,a) > (m,*) > (*,*) |
| `scope_level` | varchar(20) | NO | self |  | 数据范围: all/dept_and_children/dept/region/office/org/team/self |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `assignee_type` | varchar(10) | NO | ROLE | MUL | 主体类型: ROLE/POSITION/USER |
| `assignee_key` | varchar(50) | NO | NULL |  | 主体键: ROLE=角色编码 / POSITION=岗位ID / USER=用户ID |
| `anchor` | varchar(10) | NO | OWNER |  | 范围锚点: OWNER=按负责人 / ORG=按客户归属组织(dept_id) |

## sys_config 系统设置表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `config_key` | varchar(100) | NO | NULL | UNI |  |
| `config_value` | varchar(500) | YES | NULL |  |  |
| `description` | varchar(200) | YES | NULL |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_dept 组织架构表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `parent_id` | bigint | YES | 0 |  | 父部门ID |
| `dept_name` | varchar(100) | NO | NULL |  | 部门名称 |
| `dept_code` | varchar(50) | YES | NULL |  |  |
| `dept_type` | tinyint | YES | NULL |  | 类型 1总部 2大区 3办事处 |
| `leader_id` | bigint | YES | NULL |  | 负责人ID |
| `sort_order` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `group_id` | bigint | YES | NULL |  |  |
| `legal_entity_id` | bigint | YES | NULL |  |  |
| `dept_path` | varchar(500) | YES | NULL | MUL | 物化路径 /1/2/5/ ，用于子树查询与环路检测(V11) |
| `dept_level` | tinyint | YES | NULL |  | 层级深度，根=1(V11) |

## sys_dict_data 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `dict_type_id` | bigint | NO | NULL |  |  |
| `dict_label` | varchar(200) | NO | NULL |  |  |
| `dict_value` | varchar(200) | YES | NULL |  |  |
| `sort_order` | int | YES | 0 |  |  |
| `status` | int | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_dict_type 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `dict_name` | varchar(100) | NO | NULL |  |  |
| `dict_code` | varchar(100) | NO | NULL | UNI |  |
| `status` | int | YES | 1 |  |  |
| `built_in` | tinyint | YES | 0 |  | 是否内置(1=内置不可删) |
| `remark` | varchar(500) | YES | NULL |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_group 集团表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `group_name` | varchar(100) | NO | NULL |  |  |
| `group_code` | varchar(50) | NO | NULL | UNI |  |
| `description` | varchar(500) | YES | NULL |  |  |
| `sort_order` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_legal_entity 法人公司表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `entity_name` | varchar(100) | NO | NULL |  |  |
| `entity_code` | varchar(50) | NO | NULL | UNI |  |
| `group_id` | bigint | NO | NULL | MUL |  |
| `credit_code` | varchar(50) | YES | NULL |  |  |
| `description` | varchar(500) | YES | NULL |  |  |
| `sort_order` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_log 操作日志表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `user_id` | bigint | YES | NULL | MUL | 操作人 |
| `username` | varchar(50) | YES | NULL |  | 操作人账号 |
| `module` | varchar(50) | YES | NULL |  | 模块 |
| `action` | varchar(50) | YES | NULL |  | 操作类型 |
| `target_type` | varchar(50) | YES | NULL |  | 目标类型 |
| `target_id` | bigint | YES | NULL |  | 目标ID |
| `detail` | varchar(1000) | YES | NULL |  | 详情 |
| `ip` | varchar(50) | YES | NULL |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP | MUL |  |

## sys_menu 菜单权限表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `parent_id` | bigint | YES | 0 |  | 父菜单ID |
| `menu_name` | varchar(50) | NO | NULL |  | 菜单名称 |
| `menu_type` | tinyint | YES | NULL |  | 类型 1目录 2菜单 3按钮 |
| `path` | varchar(200) | YES | NULL |  | 路由路径 |
| `component` | varchar(200) | YES | NULL |  | 组件路径 |
| `permission` | varchar(100) | YES | NULL |  | 权限标识 |
| `icon` | varchar(100) | YES | NULL |  | 图标 |
| `sort_order` | int | YES | 0 |  |  |
| `visible` | tinyint | YES | 1 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_org_admin 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `org_id` | bigint | YES | NULL |  |  |
| `user_id` | bigint | YES | NULL | MUL |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_permission_rule 权限规则表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `name` | varchar(100) | NO | NULL |  |  |
| `code` | varchar(100) | NO | NULL | UNI |  |
| `target_entity` | varchar(50) | NO | NULL | MUL |  |
| `rule_type` | varchar(30) | NO | NULL | MUL |  |
| `target_column` | varchar(100) | YES | NULL |  |  |
| `operator` | varchar(20) | YES | NULL |  |  |
| `rule_value` | text | YES | NULL |  |  |
| `extra_config` | text | YES | NULL |  |  |
| `description` | varchar(500) | YES | NULL |  |  |
| `priority` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `create_time` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `update_time` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_position 岗位表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `name` | varchar(50) | NO | NULL |  |  |
| `code` | varchar(50) | NO | NULL | UNI |  |
| `description` | varchar(200) | YES | NULL |  |  |
| `org_id` | bigint | YES | NULL | MUL |  |
| `group_id` | bigint | NO | 1 | MUL | 集团ID |
| `status` | tinyint | YES | 1 |  |  |
| `sort` | int | YES | 0 |  |  |
| `create_time` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `update_time` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_position_rule 岗位-权限规则关联表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `position_id` | bigint | NO | NULL | MUL |  |
| `rule_id` | bigint | NO | NULL | MUL |  |

## sys_role 角色表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `group_id` | bigint | NO | 1 | MUL | 集团ID |
| `role_name` | varchar(50) | NO | NULL |  | 角色名称 |
| `role_code` | varchar(50) | NO | NULL | UNI | 角色编码 |
| `description` | varchar(200) | YES | NULL |  | 描述 |
| `data_scope` | varchar(20) | YES | self |  | 数据范围 all全部 dept_and_children本部门及下级 dept本部门 self仅自己 |
| `sort_order` | int | YES | 0 |  |  |
| `status` | tinyint | YES | 1 |  |  |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `org_id` | bigint | YES | NULL |  |  |

## sys_role_menu 角色菜单关联表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `role_id` | bigint | NO | NULL | MUL |  |
| `menu_id` | bigint | NO | NULL |  |  |

## sys_role_org 角色-组织关联表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `role_id` | bigint | NO | NULL | MUL |  |
| `org_id` | bigint | NO | NULL | MUL |  |
| `include_children` | tinyint | YES | 0 |  |  |

## sys_role_rule 角色-权限规则关联表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `role_id` | bigint | NO | NULL | MUL |  |
| `rule_id` | bigint | NO | NULL | MUL |  |

## sys_token_blacklist token撤销黑名单(DB兜底)

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `jti` | varchar(64) | NO | NULL | UNI | JWT jti |
| `user_id` | bigint | NO | NULL |  |  |
| `expires_at` | datetime | NO | NULL | MUL | token 过期时刻(行可清理) |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_user 用户表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `username` | varchar(50) | NO | NULL | UNI | 登录账号 |
| `password` | varchar(200) | NO | NULL |  | 密码(BCrypt) |
| `real_name` | varchar(50) | NO | NULL |  | 真实姓名 |
| `phone` | varchar(20) | YES | NULL |  | 手机号 |
| `email` | varchar(100) | YES | NULL |  | 邮箱 |
| `avatar` | varchar(500) | YES | NULL |  | 头像URL |
| `dept_id` | bigint | YES | NULL | MUL | 所属部门ID |
| `office_id` | bigint | YES | NULL | MUL | 所属办事处ID |
| `manager_id` | bigint | YES | NULL |  | 直接上级 |
| `data_scope_override` | varchar(20) | YES | NULL |  | 用户级数据范围覆盖(空=跟随角色；取值见 DataScopeType.code) |
| `storage_quota` | bigint | YES | 10737418240 |  | 私人存储空间配额(字节,默认10GB) |
| `storage_used` | bigint | YES | 0 |  | 已用存储空间(字节) |
| `status` | tinyint | YES | 1 |  | 状态 0禁用 1正常 |
| `created_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `updated_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |
| `deleted` | tinyint | YES | 0 |  |  |
| `org_id` | bigint | YES | NULL |  |  |
| `leave_status` | varchar(20) | YES | NULL |  |  |
| `leave_date` | datetime | YES | NULL |  |  |
| `leave_reason` | varchar(500) | YES | NULL |  |  |
| `pwd_reset_required` | tinyint | NO | 0 |  | 1=必须先改密才能使用系统(首登/管理员重置后)(V10) |
| `pwd_changed_at` | datetime | YES | NULL |  | 改密时刻(token撤销依据,DB级) |
| `perm_version` | int | NO | 0 |  | 权限版本号(缓存自愈比对) |

## sys_user_archive 

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `username` | varchar(50) | YES | NULL |  |  |
| `real_name` | varchar(100) | YES | NULL |  |  |
| `phone` | varchar(20) | YES | NULL |  |  |
| `email` | varchar(100) | YES | NULL |  |  |
| `dept_id` | bigint | YES | NULL |  |  |
| `office_id` | bigint | YES | NULL |  |  |
| `org_id` | bigint | YES | NULL |  |  |
| `manager_id` | bigint | YES | NULL |  |  |
| `role_names` | varchar(500) | YES | NULL |  |  |
| `data_scope` | varchar(50) | YES | NULL |  |  |
| `leave_status` | int | YES | NULL |  |  |
| `leave_date` | date | YES | NULL |  |  |
| `leave_reason` | varchar(500) | YES | NULL |  |  |
| `deleted_by` | bigint | YES | NULL |  |  |
| `deleted_reason` | varchar(500) | YES | NULL |  |  |
| `original_created_at` | datetime | YES | NULL |  |  |
| `deleted_at` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_user_org 用户级自定义授权组织

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `user_id` | bigint | NO | NULL | MUL |  |
| `org_id` | bigint | NO | NULL |  |  |
| `include_children` | tinyint | YES | 0 |  |  |

## sys_user_position 用户-岗位关联表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `user_id` | bigint | NO | NULL | MUL |  |
| `position_id` | bigint | NO | NULL | MUL |  |
| `is_primary` | tinyint | YES | 0 |  |  |
| `create_time` | datetime | YES | CURRENT_TIMESTAMP |  |  |

## sys_user_role 用户角色关联表

| 字段 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | bigint | NO | NULL | PRI |  |
| `user_id` | bigint | NO | NULL | MUL |  |
| `role_id` | bigint | NO | NULL |  |  |

