-- 【A4 2026-11-18】交接/组织移动审批人快照与转交（用户拍板 #4/#5：单人快照模型 + 转交仅 admin）。
--   approver_snapshot_json: 创建时解析快照 {"type","roleId","roleCode","userId","userName","source","resolvedAt"}
--     ——在途单审批人不随角色成员变化（快照）；配置被删/停用后新建单报"未配置审批人"，在途单仍按快照。
--   approver_changed_*: 管理员转交留痕（POST /requests/{id}/transfer-approver, 仅 pending, 仅 admin）。
-- 回滚预案：四列均可空、无默认值回填需求——读路径不受影响；逆 DDL 不必做（DROP COLUMN 丢审计痕迹）。
-- 上线初始化（拍板 #6）：不写脚本，管理员在审批配置手工建 handover/org_move 两条流程（指定角色，
--   生产销售总监 role_code='0' 以实际为准），文档见 out/阶段A-A3A4-拍板与开工工单-20261118-zcode.md §一#6。

ALTER TABLE `biz_lifecycle_request`
  ADD COLUMN `approver_snapshot_json` JSON NULL COMMENT '创建时审批人解析快照: type/roleId/roleCode/userId/userName/source/resolvedAt',
  ADD COLUMN `approver_changed_by` BIGINT NULL COMMENT '审批人转交操作人(仅 admin)',
  ADD COLUMN `approver_changed_at` DATETIME NULL COMMENT '审批人转交时间',
  ADD COLUMN `approver_change_reason` VARCHAR(255) NULL COMMENT '审批人转交原因';
