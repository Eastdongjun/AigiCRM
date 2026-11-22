# 测试库 Schema 漂移目录（未应用迁移）

- 测试库 Flyway 最新：`2026.11.16.9`
- 未应用迁移：12 个
- 说明：仅静态对比迁移文件与实际 information_schema；生产未触碰。

## 2026.11.17.1 — V2026.11.17.1__customer_unique_file_governance.sql

- 缺表：`biz_file_link`, `biz_file_grant`, `biz_recycle_bin`
- 缺列：
  - `biz_customer.name_norm`
  - `biz_customer.dup_exempt`
  - `biz_customer.deleted_at`
  - `biz_customer.deleted_by`
  - `biz_customer.deleted_reason`
  - `biz_customer.delete_source`
  - `biz_customer.active_name`
  - `sys_user.deleted_at`
  - `sys_user.deleted_by`
  - `sys_user.deleted_reason`
  - `sys_user.delete_source`
  - `biz_file.md5`
  - `biz_file.deleted_reason`
  - `biz_file.delete_source`
  - `biz_file_version.md5`
  - `biz_file_version.change_reason`
  - `biz_file_version.created_by`
  - `biz_incentive_record.deleted`
  - `biz_incentive_record.deleted_at`
  - `biz_incentive_record.deleted_by`
  - `biz_incentive_record.deleted_reason`
  - `biz_task.deleted_at`
  - `biz_task.deleted_by`
  - `biz_task.deleted_reason`

## 2026.11.17.2 — V2026.11.17.2__fabushi_version_rollback_org_scope.sql

- 无缺失表/列（可能仅数据变更或索引）。

## 2026.11.17.3 — V2026.11.17.3__lifecycle_handover_org_move.sql

- 缺表：`biz_lifecycle_request`, `biz_lifecycle_item`

## 2026.11.17.4 — V2026.11.17.4__chunk_upload_session.sql

- 缺表：`biz_upload_session`

## 2026.11.17.5 — V2026.11.17.5__reminder_recycle.sql

- 缺列：
  - `biz_reminder.deleted`
  - `biz_reminder.deleted_at`
  - `biz_reminder.deleted_by`
  - `biz_reminder.deleted_reason`

## 2026.11.17.6 — V2026.11.17.6__file_acl_manage_admin_only.sql

- 无缺失表/列（可能仅数据变更或索引）。

## 2026.11.17.7 — V2026.11.17.7__lifecycle_transfer_options.sql

- 缺列：
  - `biz_lifecycle_request.transfer_incentives`
  - `biz_lifecycle_request.transfer_reports`

## 2026.11.18.1 — V2026.11.18.1__file_grant_scope_columns.sql

- 缺列：
  - `biz_file_grant.space_type`
  - `biz_file_grant.legacy_ref`
  - `biz_file_grant.source`
  - `biz_file_grant.scope_mode`
  - `biz_file_grant.object_key`
  - `biz_file_grant.scope_key`

## 2026.11.18.2 — V2026.11.18.2__migrate_folder_type_default_to_file_grant.sql

- 无缺失表/列（可能仅数据变更或索引）。

## 2026.11.18.3 — V2026.11.18.3__merge_legacy_duplicate_grants.sql

- 无缺失表/列（可能仅数据变更或索引）。

## 2026.11.19.1 — V2026.11.19.1__lifecycle_approver_snapshot.sql

- 缺列：
  - `biz_lifecycle_request.approver_snapshot_json`
  - `biz_lifecycle_request.approver_changed_by`
  - `biz_lifecycle_request.approver_changed_at`
  - `biz_lifecycle_request.approver_change_reason`

## 2026.11.19.2 — V2026.11.19.2__recycle_export.sql

- 缺表：`biz_recycle_export`

