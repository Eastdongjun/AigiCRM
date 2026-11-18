package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.approval.entity.BizApprovalConfig;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【2026-11-17 阶段三】离职交接 / 组织移动（人员跨部门）一级审批 + 当前数据改派 + 精确回滚。
 *
 * <p>边界：</p>
 * <ul>
 *   <li>审批人只从 biz_approval_config 读取（flow_type=handover/org_move），不硬编码人员；</li>
 *   <li>只迁移当前数据（deleted=0 / 状态 pending），历史与已删除数据不动；</li>
 *   <li>每条受影响行写入 biz_lifecycle_item，回滚按请求精确恢复；</li>
 *   <li>继任人数据范围/角色/权限按继任人自身；copyRoles 可选，默认 false，复制时含源全部角色（是否给 admin 由审批人负责）。</li>
 * </ul>
 */
// native-sql-audit: 交接/组织移动仅 system:user:edit + 配置审批人可调用; 受 sys_user 主键/申请单约束, 单集团 tenant_id 恒 1
@Service
@RequiredArgsConstructor
public class LifecycleService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_STATUS = "status";
    private static final String LIT_APPROVER_ID = "approver_id";
    private static final String LIT_DEPT_ID = "dept_id";
    private static final String LIT_REMINDER = "reminder";
    private static final String LIT_AND_DELETED_0 = "=? AND deleted=0";
    private static final String LIT_HANDOVER = "HANDOVER";
    private static final String LIT_RESULT = "result";
    private static final String LIT_X = "请求不存在";
    private static final String LIT_SUBJECT_USER_ID = "subject_user_id";
    private static final String LIT_OWNER_ID = "owner_id";
    private static final String LIT_CUSTOMER = "customer";
    private static final String LIT_FOLDER = "folder";


    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final BizApprovalConfigMapper approvalConfigMapper;
    private final PermissionCacheService permissionCacheService;
    /** 【A4】审批人转交审计留痕；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.security.SecurityAuditService securityAudit;

    /** 【2026-11-17 P2】激励归属改派后清排行缓存；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.module.incentive.service.IncentiveService incentiveService;

    // ==================== 创建 ====================

    @Transactional
    public Map<String, Object> createHandover(Long fromUserId, Long toUserId, String reason, boolean copyRoles,
                                              boolean transferIncentives, boolean transferReports) {
        requireUserEdit();
        if (fromUserId == null || toUserId == null) throw new BusinessException("离职人与继任人不能为空");
        if (fromUserId.equals(toUserId)) throw new BusinessException("离职人与继任人不能是同一人");
        requireActiveUser(fromUserId);
        requireActiveUser(toUserId);
        ApproverSnapshot snapshot = resolveApproverSnapshot("handover");
        Long approver = snapshot.userId();
        Long applicant = SecurityUtils.getCurrentUserId();
        if (approver.equals(applicant) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("审批人不能是申请人，请配置其他审批人");
        }
        long id = insertRequest(LIT_HANDOVER, applicant, fromUserId, toUserId, null,
                reason, copyRoles, transferIncentives, transferReports, approver, null, snapshot.json());
        return requestView(id);
    }

    @Transactional
    public Map<String, Object> createOrgMove(Long userId, Long toDeptId, String reason) {
        requireUserEdit();
        if (userId == null || toDeptId == null) throw new BusinessException("人员与目标部门不能为空");
        Map<String, Object> user = requireActiveUser(userId);
        Map<String, Object> target = dept(toDeptId);
        if (target == null || !Integer.valueOf(1).equals(intVal(target.get(LIT_STATUS)))) {
            throw new BusinessException("目标部门不存在或已停用");
        }
        Long oldDept = longVal(user.get(LIT_DEPT_ID));
        if (toDeptId.equals(oldDept)) throw new BusinessException("人员已在目标部门");
        ApproverSnapshot snapshot = resolveApproverSnapshot("org_move");
        Long approver = snapshot.userId();
        Long applicant = SecurityUtils.getCurrentUserId();
        if (approver.equals(applicant) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("审批人不能是申请人，请配置其他审批人");
        }
        long id = insertRequest("ORG_MOVE", applicant, userId, null, toDeptId,
                reason, false, false, false, approver, null, snapshot.json());
        return requestView(id);
    }

    private long insertRequest(String type, Long applicant, Long subject, Long targetUser, Long targetDept,
                               String reason, boolean copyRoles, boolean transferIncentives,
                               boolean transferReports, Long approver, String snapshotJson) {
        return insertRequest(type, applicant, subject, targetUser, targetDept, reason, copyRoles,
                transferIncentives, transferReports, approver, snapshotJson, null);
    }

    /** 【A4】带审批人快照的创建（在途单审批人不随角色成员变化，拍板 #4）。 */
    private long insertRequest(String type, Long applicant, Long subject, Long targetUser, Long targetDept,
                               String reason, boolean copyRoles, boolean transferIncentives,
                               boolean transferReports, Long approver, String snapshotJson,
                               String approverSnapshotJson) {
        jdbc.update("INSERT INTO biz_lifecycle_request " +
                        "(request_type, applicant_id, subject_user_id, target_user_id, target_dept_id, reason, copy_roles, " +
                        "transfer_incentives, transfer_reports, status, approver_id, snapshot_json, approver_snapshot_json) " +
                        "VALUES (?,?,?,?,?,?,?,?,?, 'pending', ?, ?, ?)",
                type, applicant, subject, targetUser, targetDept, reason, copyRoles ? 1 : 0,
                transferIncentives ? 1 : 0, transferReports ? 1 : 0, approver, snapshotJson, approverSnapshotJson);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    // ==================== 查询 ====================

    public List<Map<String, Object>> list(String type, String status) {
        requireLifecycleRead();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT r.*, fu.real_name AS subject_name, tu.real_name AS target_user_name, au.real_name AS approver_name " +
                "FROM biz_lifecycle_request r " +
                "LEFT JOIN sys_user fu ON fu.id=r.subject_user_id " +
                "LEFT JOIN sys_user tu ON tu.id=r.target_user_id " +
                "LEFT JOIN sys_user au ON au.id=r.approver_id WHERE 1=1");
        if (type != null && !type.isBlank()) { sql.append(" AND r.request_type=?"); args.add(type.toUpperCase()); }
        if (status != null && !status.isBlank()) { sql.append(" AND r.status=?"); args.add(status); }
        sql.append(" ORDER BY r.id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> detail(Long id) {
        requireLifecycleRead();
        return requestView(id);
    }

    // ==================== 审批 ====================

    @Transactional
    public Map<String, Object> approve(Long id, String comment) {
        Map<String, Object> req = requirePendingForUpdate(id);
        Long current = SecurityUtils.getCurrentUserId();
        Long approver = longVal(req.get(LIT_APPROVER_ID));
        if (!SecurityUtils.isAdmin() && !current.equals(approver)) {
            throw new BusinessException(403, "只有配置的审批人可审批");
        }
        // 【A4 §2.4】审批人停用/删除后不能审批——admin 亦不得代批（单人快照模型，走管理员转交）
        if (!SecurityUtils.isAdmin() && !activeUser(approver)) {
            throw new BusinessException(403, "审批人已停用或删除，请联系管理员转交审批人");
        }
        String type = String.valueOf(req.get("request_type"));
        Map<String, Object> result = LIT_HANDOVER.equals(type)
                ? executeHandover(req)
                : executeOrgMove(req);
        int updated = jdbc.update("UPDATE biz_lifecycle_request SET status='approved', approved_by=?, approved_at=?, result_json=? WHERE id=? AND status='pending'",
                current, LocalDateTime.now(), toJson(result), id);
        if (updated != 1) throw new BusinessException("该请求已被其他审批人处理，请刷新");
        Map<String, Object> out = requestView(id);
        out.put(LIT_RESULT, result);
        return out;
    }

    @Transactional
    public Map<String, Object> reject(Long id, String reason) {
        Map<String, Object> req = requirePendingForUpdate(id);
        Long current = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin() && !current.equals(longVal(req.get(LIT_APPROVER_ID)))) {
            throw new BusinessException(403, "只有配置的审批人可审批");
        }
        int updated = jdbc.update("UPDATE biz_lifecycle_request SET status='rejected', rejected_by=?, rejected_at=?, reject_reason=? WHERE id=? AND status='pending'",
                current, LocalDateTime.now(), reason, id);
        if (updated != 1) throw new BusinessException("该请求已被其他审批人处理，请刷新");
        return requestView(id);
    }

    // ==================== 回滚 ====================

    @Transactional
    public Map<String, Object> rollback(Long id) {
        requireUserEdit();
        Map<String, Object> req = requestMapForUpdate(id);
        if (req == null) throw new BusinessException(LIT_X);
        if (!"approved".equals(req.get(LIT_STATUS))) throw new BusinessException("只有已通过的请求可以回滚");
        if (req.get("rolled_back_at") != null) throw new BusinessException("该请求已回滚");
        String type = String.valueOf(req.get("request_type"));
        Map<String, Integer> counts = LIT_HANDOVER.equals(type)
                ? rollbackHandover(id)
                : rollbackOrgMove(id, req);
        jdbc.update("UPDATE biz_lifecycle_request SET status='rolled_back', rolled_back_at=? WHERE id=?",
                LocalDateTime.now(), id);
        Map<String, Object> out = requestView(id);
        out.put(LIT_RESULT, counts);
        return out;
    }

    // ==================== 执行：交接 ====================

    private Map<String, Object> executeHandover(Map<String, Object> req) {
        long id = longVal(req.get("id"));
        long from = longVal(req.get(LIT_SUBJECT_USER_ID));
        long to = longVal(req.get("target_user_id"));
        Map<String, Object> target = user(to);
        if (target == null) throw new BusinessException("继任人不存在");
        Long toDept = longVal(target.get(LIT_DEPT_ID));
        Long toOffice = longVal(target.get("office_id"));
        Long toOrg = longVal(target.get("org_id"));
        if (toOrg == null) toOrg = toOffice != null ? toOffice : toDept;
        if (toDept == null || toOrg == null) throw new BusinessException("继任人未配置部门/组织，无法交接组织快照");
        if (toOffice == null) throw new BusinessException("继任人未配置办事处，无法交接客户/提醒组织快照");
        Map<String, Object> counts = new LinkedHashMap<>();

        // 【2026-11-17 P1-2】交接同时把当前数据的组织快照改派到继任人组织；旧值写明细供回滚。
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref,old_ref2,new_ref2) " +
                        "SELECT ?, 'customer_org', id, dept_id, ?, office_id, ? FROM biz_customer " +
                        "WHERE owner_id=? AND deleted=0",
                id, toDept, toOffice, from);
        counts.put("customerOrg", jdbc.update("UPDATE biz_customer SET dept_id=?, office_id=?, updated_at=NOW() WHERE owner_id=? AND deleted=0",
                toDept, toOffice, from));
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'file_org', id, org_id, ? FROM biz_file WHERE uploader_id=? AND deleted=0",
                id, toOrg, from);
        counts.put("fileOrg", jdbc.update("UPDATE biz_file SET org_id=? WHERE uploader_id=? AND deleted=0", toOrg, from));
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'folder_org', id, org_id, ? FROM biz_file_folder WHERE owner_id=? AND deleted=0",
                id, toOrg, from);
        counts.put("folderOrg", jdbc.update("UPDATE biz_file_folder SET org_id=? WHERE owner_id=? AND deleted=0", toOrg, from));
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'reminder_office', id, office_id, ? FROM biz_reminder WHERE owner_id=? AND deleted=0",
                id, toOffice, from);
        counts.put("reminderOffice", jdbc.update("UPDATE biz_reminder SET office_id=? WHERE owner_id=? AND deleted=0", toOffice, from));

        counts.put(LIT_CUSTOMER, move(LIT_CUSTOMER, "biz_customer", LIT_OWNER_ID, id, from, to));
        counts.put("task", move("task", "biz_task", "assignee_id", id, from, to));
        counts.put(LIT_REMINDER, moveNoDeleted(LIT_REMINDER, "biz_reminder", LIT_OWNER_ID, id, from, to));
        counts.put("file", move("file", "biz_file", "uploader_id", id, from, to));
        counts.put(LIT_FOLDER, move(LIT_FOLDER, "biz_file_folder", LIT_OWNER_ID, id, from, to));

        // 审批：在办申请转给继任人；待办审批节点转给继任人（历史已办不动）
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'approval', id, applicant_id, ? FROM biz_approval " +
                        "WHERE applicant_id=? AND status IN ('pending','reviewing')",
                id, to, from);
        counts.put("approval", jdbc.update("UPDATE biz_approval SET applicant_id=? WHERE applicant_id=? AND status IN ('pending','reviewing')", to, from));

        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'approval_node', id, approver_id, ? FROM biz_approval_node " +
                        "WHERE approver_id=? AND status='pending'",
                id, to, from);
        counts.put("approvalNode", jdbc.update("UPDATE biz_approval_node SET approver_id=? WHERE approver_id=? AND status='pending'", to, from));

        int roles = 0;
        if (Integer.valueOf(1).equals(intVal(req.get("copy_roles")))) {
            List<Long> roleIds = jdbc.queryForList(
                    "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, from);
            for (Long roleId : roleIds) {
                int inserted = jdbc.update("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES(?,?)", to, roleId);
                if (inserted == 1) {
                    jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) VALUES(?, 'user_role', ?, 0, ?)",
                            id, roleId, to);
                    roles++;
                }
            }
        }
        counts.put("role", roles);

        // 【2026-11-17 P1-5】可选：激励记录/直属下级随交接迁移（默认关闭）。
        int incentives = 0;
        if (Integer.valueOf(1).equals(intVal(req.get("transfer_incentives")))) {
            jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                            "SELECT ?, 'incentive', id, user_id, ? FROM biz_incentive_record WHERE user_id=? AND deleted=0",
                    id, to, from);
            incentives = jdbc.update("UPDATE biz_incentive_record SET user_id=? WHERE user_id=? AND deleted=0", to, from);
        }
        counts.put("incentive", incentives);

        int reports = 0;
        if (Integer.valueOf(1).equals(intVal(req.get("transfer_reports")))) {
            jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                            "SELECT ?, 'report_manager', id, manager_id, ? FROM sys_user WHERE manager_id=? AND deleted=0",
                    id, to, from);
            reports = jdbc.update("UPDATE sys_user SET manager_id=? WHERE manager_id=? AND deleted=0", to, from);
        }
        counts.put("report", reports);
        if (incentives > 0 && incentiveService != null) incentiveService.evictScoreboardCacheAfterRecycle();
        if (reports > 0 || roles > 0) permissionCacheService.evictAll();
        return counts;
    }

    private int move(String bizType, String table, String col, long requestId, long from, long to) {
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, ?, id, " + col + ", ? FROM " + table + " WHERE " + col + LIT_AND_DELETED_0,
                requestId, bizType, to, from);
        return jdbc.update("UPDATE " + table + " SET " + col + "=? WHERE " + col + LIT_AND_DELETED_0,
                to, from);
    }

    /** 提醒表在 V2026.11.17.5 后已有 deleted；只迁当前数据，回收站行不动。 */
    private int moveNoDeleted(String bizType, String table, String col, long requestId, long from, long to) {
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, ?, id, " + col + ", ? FROM " + table + " WHERE " + col + LIT_AND_DELETED_0,
                requestId, bizType, to, from);
        return jdbc.update("UPDATE " + table + " SET " + col + "=? WHERE " + col + LIT_AND_DELETED_0, to, from);
    }

    // ==================== 执行：组织移动 ====================

    private Map<String, Object> executeOrgMove(Map<String, Object> req) {
        long id = longVal(req.get("id"));
        long userId = longVal(req.get(LIT_SUBJECT_USER_ID));
        long toDept = longVal(req.get("target_dept_id"));
        Map<String, Object> user = user(userId);
        if (user == null) throw new BusinessException("人员不存在");
        Long oldDept = longVal(user.get(LIT_DEPT_ID));
        Long oldOffice = longVal(user.get("office_id"));
        Long oldOrg = longVal(user.get("org_id"));
        Long toOffice = nearestOffice(toDept);
        Long toOrg = toDept;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deptId", oldDept);
        snapshot.put("officeId", oldOffice);
        snapshot.put("orgId", oldOrg);
        snapshot.put("newDeptId", toDept);
        snapshot.put("newOfficeId", toOffice);
        snapshot.put("newOrgId", toOrg);
        jdbc.update("UPDATE biz_lifecycle_request SET snapshot_json=? WHERE id=?", toJson(snapshot), id);

        Map<String, Object> counts = new LinkedHashMap<>();
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref,old_ref2,new_ref2) " +
                        "SELECT ?, 'customer_org', id, dept_id, ?, office_id, ? FROM biz_customer " +
                        "WHERE owner_id=? AND deleted=0",
                id, toDept, toOffice, userId);
        counts.put(LIT_CUSTOMER, jdbc.update("UPDATE biz_customer SET dept_id=?, office_id=?, updated_at=NOW() WHERE owner_id=? AND deleted=0",
                toDept, toOffice, userId));

        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'file_org', id, org_id, ? FROM biz_file WHERE uploader_id=? AND deleted=0",
                id, toOrg, userId);
        counts.put("file", jdbc.update("UPDATE biz_file SET org_id=? WHERE uploader_id=? AND deleted=0", toOrg, userId));

        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'folder_org', id, org_id, ? FROM biz_file_folder WHERE owner_id=? AND deleted=0",
                id, toOrg, userId);
        counts.put(LIT_FOLDER, jdbc.update("UPDATE biz_file_folder SET org_id=? WHERE owner_id=? AND deleted=0", toOrg, userId));

        // 【2026-11-17 P1-4】提醒的 office_id 同步到新办事处；旧值写明细供回滚。
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'reminder_office', id, office_id, ? FROM biz_reminder WHERE owner_id=? AND deleted=0",
                id, toOffice, userId);
        counts.put(LIT_REMINDER, jdbc.update("UPDATE biz_reminder SET office_id=? WHERE owner_id=? AND deleted=0", toOffice, userId));

        counts.put("user", jdbc.update("UPDATE sys_user SET dept_id=?, office_id=?, org_id=?, perm_version=perm_version+1, updated_at=NOW() WHERE id=?",
                toDept, toOffice, toOrg, userId));
        permissionCacheService.evict(userId);
        return counts;
    }

    // ==================== 回滚 ====================

    private Map<String, Integer> rollbackHandover(long id) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("customerOrg", jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +
                "SET c.dept_id=i.old_ref, c.office_id=i.old_ref2, c.updated_at=NOW() " +
                "WHERE i.request_id=? AND i.biz_type='customer_org' AND c.dept_id=i.new_ref AND c.office_id=i.new_ref2", id));
        counts.put("fileOrg", jdbc.update("UPDATE biz_file f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.org_id=i.old_ref WHERE i.request_id=? AND i.biz_type='file_org' AND f.org_id=i.new_ref", id));
        counts.put("folderOrg", jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.org_id=i.old_ref WHERE i.request_id=? AND i.biz_type='folder_org' AND f.org_id=i.new_ref", id));
        counts.put("reminderOffice", jdbc.update("UPDATE biz_reminder r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +
                "SET r.office_id=i.old_ref WHERE i.request_id=? AND i.biz_type='reminder_office' AND r.office_id=i.new_ref", id));
        counts.put(LIT_CUSTOMER, jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +
                "SET c.owner_id=i.old_ref, c.updated_at=NOW() WHERE i.request_id=? AND i.biz_type='customer' AND c.owner_id=i.new_ref", id));
        counts.put("task", jdbc.update("UPDATE biz_task t JOIN biz_lifecycle_item i ON i.biz_id=t.id " +
                "SET t.assignee_id=i.old_ref, t.updated_at=NOW() WHERE i.request_id=? AND i.biz_type='task' AND t.assignee_id=i.new_ref", id));
        counts.put(LIT_REMINDER, jdbc.update("UPDATE biz_reminder r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +
                "SET r.owner_id=i.old_ref WHERE i.request_id=? AND i.biz_type='reminder' AND r.owner_id=i.new_ref", id));
        counts.put("file", jdbc.update("UPDATE biz_file f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.uploader_id=i.old_ref WHERE i.request_id=? AND i.biz_type='file' AND f.uploader_id=i.new_ref", id));
        counts.put(LIT_FOLDER, jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.owner_id=i.old_ref WHERE i.request_id=? AND i.biz_type='folder' AND f.owner_id=i.new_ref", id));
        counts.put("approval", jdbc.update("UPDATE biz_approval a JOIN biz_lifecycle_item i ON i.biz_id=a.id " +
                "SET a.applicant_id=i.old_ref WHERE i.request_id=? AND i.biz_type='approval' AND a.applicant_id=i.new_ref", id));
        counts.put("approvalNode", jdbc.update("UPDATE biz_approval_node n JOIN biz_lifecycle_item i ON i.biz_id=n.id " +
                "SET n.approver_id=i.old_ref WHERE i.request_id=? AND i.biz_type='approval_node' AND n.approver_id=i.new_ref", id));
        counts.put("role", jdbc.update("DELETE ur FROM sys_user_role ur JOIN biz_lifecycle_item i " +
                "ON i.biz_id=ur.role_id AND i.new_ref=ur.user_id " +
                "WHERE i.request_id=? AND i.biz_type='user_role'", id));
        counts.put("incentive", jdbc.update("UPDATE biz_incentive_record r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +
                "SET r.user_id=i.old_ref WHERE i.request_id=? AND i.biz_type='incentive' AND r.user_id=i.new_ref", id));
        counts.put("report", jdbc.update("UPDATE sys_user u JOIN biz_lifecycle_item i ON i.biz_id=u.id " +
                "SET u.manager_id=i.old_ref WHERE i.request_id=? AND i.biz_type='report_manager' AND u.manager_id=i.new_ref", id));
        return counts;
    }

    private Map<String, Integer> rollbackOrgMove(long id, Map<String, Object> req) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Object> snapshot = parseMap(req.get("snapshot_json"));
        Long oldDept = longVal(snapshot.get("deptId"));
        Long oldOffice = longVal(snapshot.get("officeId"));
        Long oldOrg = longVal(snapshot.get("orgId"));
        long userId = longVal(req.get(LIT_SUBJECT_USER_ID));
        Long newDept = longVal(snapshot.get("newDeptId"));
        Long newOffice = longVal(snapshot.get("newOfficeId"));
        Long newOrg = longVal(snapshot.get("newOrgId"));
        counts.put("user", jdbc.update("UPDATE sys_user SET dept_id=?, office_id=?, org_id=?, perm_version=perm_version+1, updated_at=NOW() " +
                        "WHERE id=? AND dept_id <=> ? AND office_id <=> ? AND org_id <=> ?",
                oldDept, oldOffice, oldOrg, userId, newDept, newOffice, newOrg));
        counts.put(LIT_CUSTOMER, jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +
                "SET c.dept_id=i.old_ref, c.office_id=i.old_ref2, c.updated_at=NOW() " +
                "WHERE i.request_id=? AND i.biz_type='customer_org' AND c.dept_id=i.new_ref AND c.office_id=i.new_ref2", id));
        counts.put("file", jdbc.update("UPDATE biz_file f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.org_id=i.old_ref WHERE i.request_id=? AND i.biz_type='file_org' AND f.org_id=i.new_ref", id));
        counts.put(LIT_FOLDER, jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.org_id=i.old_ref WHERE i.request_id=? AND i.biz_type='folder_org' AND f.org_id=i.new_ref", id));
        counts.put(LIT_REMINDER, jdbc.update("UPDATE biz_reminder r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +
                "SET r.office_id=i.old_ref WHERE i.request_id=? AND i.biz_type='reminder_office' AND r.office_id=i.new_ref", id));
        permissionCacheService.evict(userId);
        return counts;
    }

    // ==================== 基础设施 ====================

    private void requireLifecycleRead() {
        if (!SecurityUtils.isAdmin()
                && !SecurityUtils.hasAuthority("system:user:edit")
                && !SecurityUtils.hasAuthority("approval:view")) {
            throw new BusinessException(403, "需要人员管理或审批查看权限");
        }
    }

    private void requireUserEdit() {
        if (!SecurityUtils.isAdmin() && !SecurityUtils.hasAuthority("system:user:edit")) {
            throw new BusinessException(403, "需要人员管理权限");
        }
    }

    private Map<String, Object> requireActiveUser(Long id) {
        Map<String, Object> u = user(id);
        if (u == null || !Integer.valueOf(1).equals(intVal(u.get(LIT_STATUS)))
                || Integer.valueOf(1).equals(intVal(u.get("deleted")))) {
            throw new BusinessException("人员不存在或已停用");
        }
        return u;
    }

    private Map<String, Object> user(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sys_user WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> dept(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sys_dept WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 目标部门最近办事处(dept_type=3)；自身是办事处则取自身。 */
    private Long nearestOffice(Long deptId) {
        Long cur = deptId;
        for (int i = 0; i < 50 && cur != null; i++) {
            Map<String, Object> d = dept(cur);
            if (d == null) break;
            Integer type = intVal(d.get("dept_type"));
            if (type != null && type == 3) return cur;
            cur = longVal(d.get("parent_id"));
        }
        return null;
    }

    /** 【A4 拍板 #4】审批人解析快照：单人（角色下第一个启用成员/指定用户），含来源与解析时间。 */
    public record ApproverSnapshot(Long userId, String json) {}

    private Long resolveApprover(String flowType) {
        return resolveApproverSnapshot(flowType).userId();
    }

    private ApproverSnapshot resolveApproverSnapshot(String flowType) {
        List<BizApprovalConfig> configs = approvalConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizApprovalConfig>()
                        .eq(BizApprovalConfig::getFlowType, flowType)
                        .eq(BizApprovalConfig::getStatus, 1)
                        .orderByAsc(BizApprovalConfig::getSortOrder)
                        .orderByAsc(BizApprovalConfig::getId));
        for (BizApprovalConfig c : configs) {
            Long id = firstApprover(c);
            if (id != null) return snapshotOf(c, id);
        }
        throw new BusinessException("未配置「" + flowType + "」审批人，请先在审批配置中设置一级审批人（或角色下无启用人员）");
    }

    /** 快照 JSON：type/roleId/roleCode/userId/userName/source/resolvedAt（在途单不随角色成员变化）。 */
    private ApproverSnapshot snapshotOf(BizApprovalConfig c, Long userId) {
        String type = c.getApproverType() == null ? "USER" : c.getApproverType().toUpperCase();
        long configRefId = 0;
        String roleCode = null;
        try {
            JsonNode node = JSON.readTree(c.getApproverIds() == null ? "[]" : c.getApproverIds());
            if (node.isArray() && !node.isEmpty()) configRefId = node.get(0).asLong(0);
            if ("ROLE".equals(type) && configRefId > 0) {
                roleCode = jdbc.queryForObject("SELECT role_code FROM sys_role WHERE id=?", String.class, configRefId);
            }
        } catch (Exception ignored) {
            // 解析失败按无 roleCode 快照
        }
        String userName = null;
        try {
            userName = jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=?", String.class, userId);
        } catch (Exception ignored) {
            // 已删用户名留空
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (configRefId > 0) m.put("roleId", configRefId);
        if (roleCode != null) m.put("roleCode", roleCode);
        m.put("userId", userId);
        if (userName != null) m.put("userName", userName);
        m.put("source", "config:" + type + (configRefId > 0 ? "#" + configRefId : "") + ("ROLE".equals(type) ? " first-active" : ""));
        m.put("resolvedAt", LocalDateTime.now().withNano(0).toString());
        try {
            return new ApproverSnapshot(userId, JSON.writeValueAsString(m));
        } catch (Exception e) {
            return new ApproverSnapshot(userId, "{}");
        }
    }

    private Long firstApprover(BizApprovalConfig c) {
        try {
            JsonNode node = JSON.readTree(c.getApproverIds() == null ? "[]" : c.getApproverIds());
            if (!node.isArray() || node.isEmpty()) return null;
            long id = node.get(0).asLong(0);
            if (id <= 0) return null;
            String type = c.getApproverType() == null ? "USER" : c.getApproverType().toUpperCase();
            return switch (type) {
                case "USER" -> activeUser(id) ? id : null;
                case "ROLE" -> firstUser("SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id " +
                        "WHERE ur.role_id=? AND u.deleted=0 AND u.status=1 ORDER BY u.id LIMIT 1", id);
                case "DEPT" -> firstUser("SELECT id FROM sys_user WHERE dept_id=? AND deleted=0 AND status=1 ORDER BY id LIMIT 1", id);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private boolean activeUser(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND deleted=0 AND status=1", Integer.class, id);
        return n != null && n > 0;
    }

    private Long firstUser(String sql, long arg) {
        List<Long> rows = jdbc.queryForList(sql, Long.class, arg);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ==================== 【A4 拍板 #4/#5】审批人转交（仅 admin, 仅 pending） ====================

    /**
     * 管理员转交在途单审批人：写 approver_id/changed_* + 审计；单人快照模型下不自动换人，
     * 原审批人停用/删除时由此通道显式转交。转交权限收窄为仅 admin（拍板 #5：审批人自转有绕过面）。
     */
    @Transactional
    public Map<String, Object> transferApprover(Long id, Long newApproverId, String reason) {
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "审批人转交仅系统管理员可操作");
        }
        if (newApproverId == null || newApproverId <= 0) throw new BusinessException("新审批人不能为空");
        Map<String, Object> req = requirePendingForUpdate(id);
        if (!activeUser(newApproverId)) throw new BusinessException("新审批人不存在或已停用");
        if (newApproverId.equals(longVal(req.get("applicant_id")))) {
            throw new BusinessException("审批人不能是申请人");
        }
        if (newApproverId.equals(longVal(req.get(LIT_APPROVER_ID)))) {
            throw new BusinessException("新审批人与当前审批人相同");
        }
        Long operator = SecurityUtils.getCurrentUserId();
        jdbc.update("UPDATE biz_lifecycle_request SET approver_id=?, approver_changed_by=?, " +
                        "approver_changed_at=NOW(), approver_change_reason=? WHERE id=? AND status='pending'",
                newApproverId, operator, reason == null ? "" : reason, id);
        if (securityAudit != null) {
            try {
                securityAudit.permissionChange("lifecycle_approver_transfer",
                        "request=" + id + " old=" + req.get(LIT_APPROVER_ID) + " new=" + newApproverId
                                + " by=" + operator + " reason=" + (reason == null ? "" : reason));
            } catch (Exception ignored) {
                // 审计失败不阻断转交；changed_* 字段已留痕
            }
        }
        return requestView(id);
    }

    private Map<String, Object> requirePendingForUpdate(Long id) {
        Map<String, Object> req = requestMapForUpdate(id);
        if (req == null) throw new BusinessException(LIT_X);
        if (!"pending".equals(req.get(LIT_STATUS))) throw new BusinessException("该请求已处理");
        return req;
    }

    private Map<String, Object> requestMap(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM biz_lifecycle_request WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> requestMapForUpdate(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM biz_lifecycle_request WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> requestView(Long id) {
        Map<String, Object> req = requestMap(id);
        if (req == null) throw new BusinessException(LIT_X);
        Map<String, Object> out = new LinkedHashMap<>(req);
        Object snapshot = req.get("snapshot_json");
        Object result = req.get("result_json");
        Object approverSnapshot = req.get("approver_snapshot_json");
        if (snapshot instanceof String s) out.put("snapshot", parseMap(s));
        if (result instanceof String s) out.put(LIT_RESULT, parseMap(s));
        // 【A4】审批人快照解析为嵌套对象（UI 显示来源）；转交仅 admin（拍板 #5）
        if (approverSnapshot instanceof String s) out.put("approverSnapshot", parseMap(s));
        out.put("canTransferApprover", SecurityUtils.isAdmin() && "pending".equals(req.get(LIT_STATUS)));
        return out;
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return Map.of();
        try {
            if (raw instanceof Map<?, ?> m) {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
            return JSON.readValue(String.valueOf(raw), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    private Integer intVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Long longVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
