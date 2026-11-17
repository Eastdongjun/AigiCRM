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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final BizApprovalConfigMapper approvalConfigMapper;
    private final PermissionCacheService permissionCacheService;

    // ==================== 创建 ====================

    @Transactional
    public Map<String, Object> createHandover(Long fromUserId, Long toUserId, String reason, boolean copyRoles) {
        requireUserEdit();
        if (fromUserId == null || toUserId == null) throw new BusinessException("离职人与继任人不能为空");
        if (fromUserId.equals(toUserId)) throw new BusinessException("离职人与继任人不能是同一人");
        requireActiveUser(fromUserId);
        requireActiveUser(toUserId);
        Long approver = resolveApprover("handover");
        Long applicant = SecurityUtils.getCurrentUserId();
        if (approver.equals(applicant) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("审批人不能是申请人，请配置其他审批人");
        }
        long id = insertRequest("HANDOVER", applicant, fromUserId, toUserId, null,
                reason, copyRoles, approver, null);
        return requestView(id);
    }

    @Transactional
    public Map<String, Object> createOrgMove(Long userId, Long toDeptId, String reason) {
        requireUserEdit();
        if (userId == null || toDeptId == null) throw new BusinessException("人员与目标部门不能为空");
        Map<String, Object> user = requireActiveUser(userId);
        Map<String, Object> target = dept(toDeptId);
        if (target == null || !Integer.valueOf(1).equals(intVal(target.get("status")))) {
            throw new BusinessException("目标部门不存在或已停用");
        }
        Long oldDept = longVal(user.get("dept_id"));
        if (toDeptId.equals(oldDept)) throw new BusinessException("人员已在目标部门");
        Long approver = resolveApprover("org_move");
        Long applicant = SecurityUtils.getCurrentUserId();
        if (approver.equals(applicant) && !SecurityUtils.isAdmin()) {
            throw new BusinessException("审批人不能是申请人，请配置其他审批人");
        }
        long id = insertRequest("ORG_MOVE", applicant, userId, null, toDeptId,
                reason, false, approver, null);
        return requestView(id);
    }

    private long insertRequest(String type, Long applicant, Long subject, Long targetUser, Long targetDept,
                               String reason, boolean copyRoles, Long approver, String snapshotJson) {
        jdbc.update("INSERT INTO biz_lifecycle_request " +
                        "(request_type, applicant_id, subject_user_id, target_user_id, target_dept_id, reason, copy_roles, status, approver_id, snapshot_json) " +
                        "VALUES (?,?,?,?,?,?,?, 'pending', ?, ?)",
                type, applicant, subject, targetUser, targetDept, reason, copyRoles ? 1 : 0,
                approver, snapshotJson);
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
        Map<String, Object> req = requirePending(id);
        Long current = SecurityUtils.getCurrentUserId();
        Long approver = longVal(req.get("approver_id"));
        if (!SecurityUtils.isAdmin() && !current.equals(approver)) {
            throw new BusinessException(403, "只有配置的审批人可审批");
        }
        String type = String.valueOf(req.get("request_type"));
        Map<String, Object> result = "HANDOVER".equals(type)
                ? executeHandover(req)
                : executeOrgMove(req);
        jdbc.update("UPDATE biz_lifecycle_request SET status='approved', approved_by=?, approved_at=?, result_json=? WHERE id=? AND status='pending'",
                current, LocalDateTime.now(), toJson(result), id);
        Map<String, Object> out = requestView(id);
        out.put("result", result);
        return out;
    }

    @Transactional
    public Map<String, Object> reject(Long id, String reason) {
        Map<String, Object> req = requirePending(id);
        Long current = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin() && !current.equals(longVal(req.get("approver_id")))) {
            throw new BusinessException(403, "只有配置的审批人可审批");
        }
        jdbc.update("UPDATE biz_lifecycle_request SET status='rejected', rejected_by=?, rejected_at=?, reject_reason=? WHERE id=? AND status='pending'",
                current, LocalDateTime.now(), reason, id);
        return requestView(id);
    }

    // ==================== 回滚 ====================

    @Transactional
    public Map<String, Object> rollback(Long id) {
        requireUserEdit();
        Map<String, Object> req = requestMap(id);
        if (req == null) throw new BusinessException("请求不存在");
        if (!"approved".equals(req.get("status"))) throw new BusinessException("只有已通过的请求可以回滚");
        if (req.get("rolled_back_at") != null) throw new BusinessException("该请求已回滚");
        String type = String.valueOf(req.get("request_type"));
        Map<String, Integer> counts = "HANDOVER".equals(type)
                ? rollbackHandover(id)
                : rollbackOrgMove(id, req);
        jdbc.update("UPDATE biz_lifecycle_request SET status='rolled_back', rolled_back_at=? WHERE id=?",
                LocalDateTime.now(), id);
        Map<String, Object> out = requestView(id);
        out.put("result", counts);
        return out;
    }

    // ==================== 执行：交接 ====================

    private Map<String, Object> executeHandover(Map<String, Object> req) {
        long id = longVal(req.get("id"));
        long from = longVal(req.get("subject_user_id"));
        long to = longVal(req.get("target_user_id"));
        Map<String, Object> counts = new LinkedHashMap<>();

        counts.put("customer", move("customer", "biz_customer", "owner_id", id, from, to));
        counts.put("task", move("task", "biz_task", "assignee_id", id, from, to));
        counts.put("reminder", moveNoDeleted("reminder", "biz_reminder", "owner_id", id, from, to));
        counts.put("file", move("file", "biz_file", "uploader_id", id, from, to));
        counts.put("folder", move("folder", "biz_file_folder", "owner_id", id, from, to));

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
        return counts;
    }

    private int move(String bizType, String table, String col, long requestId, long from, long to) {
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, ?, id, " + col + ", ? FROM " + table + " WHERE " + col + "=? AND deleted=0",
                requestId, bizType, to, from);
        return jdbc.update("UPDATE " + table + " SET " + col + "=? WHERE " + col + "=? AND deleted=0",
                to, from);
    }

    /** 提醒表无 deleted 列（状态机 pending/processed），单独口径。 */
    private int moveNoDeleted(String bizType, String table, String col, long requestId, long from, long to) {
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, ?, id, " + col + ", ? FROM " + table + " WHERE " + col + "=?",
                requestId, bizType, to, from);
        return jdbc.update("UPDATE " + table + " SET " + col + "=? WHERE " + col + "=?", to, from);
    }

    // ==================== 执行：组织移动 ====================

    private Map<String, Object> executeOrgMove(Map<String, Object> req) {
        long id = longVal(req.get("id"));
        long userId = longVal(req.get("subject_user_id"));
        long toDept = longVal(req.get("target_dept_id"));
        Map<String, Object> user = user(userId);
        if (user == null) throw new BusinessException("人员不存在");
        Long oldDept = longVal(user.get("dept_id"));
        Long oldOffice = longVal(user.get("office_id"));
        Long oldOrg = longVal(user.get("org_id"));
        Long toOffice = nearestOffice(toDept);
        Long toOrg = toDept;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deptId", oldDept);
        snapshot.put("officeId", oldOffice);
        snapshot.put("orgId", oldOrg);
        jdbc.update("UPDATE biz_lifecycle_request SET snapshot_json=? WHERE id=?", toJson(snapshot), id);

        Map<String, Object> counts = new LinkedHashMap<>();
        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref,old_ref2,new_ref2) " +
                        "SELECT ?, 'customer_org', id, dept_id, ?, office_id, ? FROM biz_customer " +
                        "WHERE owner_id=? AND deleted=0",
                id, toDept, toOffice, userId);
        counts.put("customer", jdbc.update("UPDATE biz_customer SET dept_id=?, office_id=?, updated_at=NOW() WHERE owner_id=? AND deleted=0",
                toDept, toOffice, userId));

        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'file_org', id, org_id, ? FROM biz_file WHERE uploader_id=? AND deleted=0",
                id, toOrg, userId);
        counts.put("file", jdbc.update("UPDATE biz_file SET org_id=? WHERE uploader_id=? AND deleted=0", toOrg, userId));

        jdbc.update("INSERT INTO biz_lifecycle_item(request_id,biz_type,biz_id,old_ref,new_ref) " +
                        "SELECT ?, 'folder_org', id, org_id, ? FROM biz_file_folder WHERE owner_id=? AND deleted=0",
                id, toOrg, userId);
        counts.put("folder", jdbc.update("UPDATE biz_file_folder SET org_id=? WHERE owner_id=? AND deleted=0", toOrg, userId));

        counts.put("user", jdbc.update("UPDATE sys_user SET dept_id=?, office_id=?, org_id=?, perm_version=perm_version+1, updated_at=NOW() WHERE id=?",
                toDept, toOffice, toOrg, userId));
        permissionCacheService.evict(userId);
        return counts;
    }

    // ==================== 回滚 ====================

    private Map<String, Integer> rollbackHandover(long id) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("customer", jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +
                "SET c.owner_id=i.old_ref, c.updated_at=NOW() WHERE i.request_id=? AND i.biz_type='customer' AND c.owner_id=i.new_ref", id));
        counts.put("task", jdbc.update("UPDATE biz_task t JOIN biz_lifecycle_item i ON i.biz_id=t.id " +
                "SET t.assignee_id=i.old_ref, t.updated_at=NOW() WHERE i.request_id=? AND i.biz_type='task' AND t.assignee_id=i.new_ref", id));
        counts.put("reminder", jdbc.update("UPDATE biz_reminder r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +
                "SET r.owner_id=i.old_ref WHERE i.request_id=? AND i.biz_type='reminder' AND r.owner_id=i.new_ref", id));
        counts.put("file", jdbc.update("UPDATE biz_file f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.uploader_id=i.old_ref WHERE i.request_id=? AND i.biz_type='file' AND f.uploader_id=i.new_ref", id));
        counts.put("folder", jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.owner_id=i.old_ref WHERE i.request_id=? AND i.biz_type='folder' AND f.owner_id=i.new_ref", id));
        counts.put("approval", jdbc.update("UPDATE biz_approval a JOIN biz_lifecycle_item i ON i.biz_id=a.id " +
                "SET a.applicant_id=i.old_ref WHERE i.request_id=? AND i.biz_type='approval' AND a.applicant_id=i.new_ref", id));
        counts.put("approvalNode", jdbc.update("UPDATE biz_approval_node n JOIN biz_lifecycle_item i ON i.biz_id=n.id " +
                "SET n.approver_id=i.old_ref WHERE i.request_id=? AND i.biz_type='approval_node' AND n.approver_id=i.new_ref", id));
        counts.put("role", jdbc.update("DELETE ur FROM sys_user_role ur JOIN biz_lifecycle_item i " +
                "ON i.biz_id=ur.role_id AND i.new_ref=ur.user_id " +
                "WHERE i.request_id=? AND i.biz_type='user_role'", id));
        return counts;
    }

    private Map<String, Integer> rollbackOrgMove(long id, Map<String, Object> req) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Object> snapshot = parseMap(req.get("snapshot_json"));
        Long oldDept = longVal(snapshot.get("deptId"));
        Long oldOffice = longVal(snapshot.get("officeId"));
        Long oldOrg = longVal(snapshot.get("orgId"));
        long userId = longVal(req.get("subject_user_id"));
        counts.put("user", jdbc.update("UPDATE sys_user SET dept_id=?, office_id=?, org_id=?, perm_version=perm_version+1, updated_at=NOW() WHERE id=?",
                oldDept, oldOffice, oldOrg, userId));
        counts.put("customer", jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +
                "SET c.dept_id=i.old_ref, c.office_id=i.old_ref2, c.updated_at=NOW() " +
                "WHERE i.request_id=? AND i.biz_type='customer_org'", id));
        counts.put("file", jdbc.update("UPDATE biz_file f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.org_id=i.old_ref WHERE i.request_id=? AND i.biz_type='file_org'", id));
        counts.put("folder", jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +
                "SET f.org_id=i.old_ref WHERE i.request_id=? AND i.biz_type='folder_org'", id));
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
        if (u == null || !Integer.valueOf(1).equals(intVal(u.get("status")))
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

    private Long resolveApprover(String flowType) {
        List<BizApprovalConfig> configs = approvalConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizApprovalConfig>()
                        .eq(BizApprovalConfig::getFlowType, flowType)
                        .eq(BizApprovalConfig::getStatus, 1)
                        .orderByAsc(BizApprovalConfig::getSortOrder)
                        .orderByAsc(BizApprovalConfig::getId));
        for (BizApprovalConfig c : configs) {
            Long id = firstApprover(c);
            if (id != null) return id;
        }
        throw new BusinessException("未配置「" + flowType + "」审批人，请先在审批配置中设置一级审批人");
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

    private Map<String, Object> requirePending(Long id) {
        Map<String, Object> req = requestMap(id);
        if (req == null) throw new BusinessException("请求不存在");
        if (!"pending".equals(req.get("status"))) throw new BusinessException("该请求已处理");
        return req;
    }

    private Map<String, Object> requestMap(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM biz_lifecycle_request WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> requestView(Long id) {
        Map<String, Object> req = requestMap(id);
        if (req == null) throw new BusinessException("请求不存在");
        Map<String, Object> out = new LinkedHashMap<>(req);
        Object snapshot = req.get("snapshot_json");
        Object result = req.get("result_json");
        if (snapshot instanceof String s) out.put("snapshot", parseMap(s));
        if (result instanceof String s) out.put("result", parseMap(s));
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
