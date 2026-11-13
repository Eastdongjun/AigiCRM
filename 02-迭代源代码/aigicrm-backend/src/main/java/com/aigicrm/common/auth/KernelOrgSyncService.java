package com.aigicrm.common.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 【P0-4 修复 2026-11-13】任职/闭包运行期维护(规格 WP2 表的写点)。
 * 触发: PermissionCacheService 汇聚点——evict(userId)→单用户任职同步; evictAll(组织/岗位/
 * 规则等全局写)→闭包+全量任职重建。幂等(全删重插), 表未就绪安全(旧轨不受影响)。
 * sys_position_role: 本期动作轴仍走 sys_role_menu(P0-5 方案二), 该表留待动作轴迁移批启用,
 * 见 docs/permission/切流回滚Runbook-WP8.md 挂账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KernelOrgSyncService {

    private final JdbcTemplate jdbc;

    /** 单用户任职同步(岗位任职→无岗位者 dept_id 主职; 与 V2026.11.13.3 回填同口径; 事务原子=并发登录不见空窗) */
    @org.springframework.transaction.annotation.Transactional
    public void syncUserEmployment(Long userId) {
        if (userId == null) return;
        try {
            jdbc.update("DELETE FROM sys_employment WHERE user_id = ?", userId);
            jdbc.update(
                    "INSERT IGNORE INTO sys_employment (user_id, org_id, position_id, is_primary, status) " +
                    "SELECT up.user_id, p.org_id, p.id, COALESCE(up.is_primary, 1), 1 " +
                    "FROM sys_user_position up " +
                    "JOIN sys_position p ON p.id = up.position_id AND p.org_id IS NOT NULL AND p.status = 1 " +
                    "WHERE up.user_id = ? " +
                    "AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = up.user_id AND u.deleted = 0)", userId);
            jdbc.update(
                    "INSERT IGNORE INTO sys_employment (user_id, org_id, position_id, is_primary, status) " +
                    "SELECT u.id, u.dept_id, 0, 1, 1 " +
                    "FROM sys_user u JOIN sys_dept d ON d.id = u.dept_id " +
                    "WHERE u.id = ? AND u.deleted = 0 " +
                    "AND NOT EXISTS (SELECT 1 FROM sys_employment e WHERE e.user_id = u.id)", userId);
        } catch (Exception e) {
            log.warn("任职同步失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 组织闭包全量重建(组织写点后; parent_id 递归, 深度护栏 20; 事务原子) */
    @org.springframework.transaction.annotation.Transactional
    public void rebuildClosure() {
        try {
            jdbc.update("DELETE FROM sys_org_closure");
            jdbc.update(
                    "INSERT INTO sys_org_closure (ancestor_id, descendant_id, depth) " +
                    "WITH RECURSIVE closure AS ( " +
                    "  SELECT id AS ancestor_id, id AS descendant_id, 0 AS depth FROM sys_dept WHERE status = 1 " +
                    "  UNION ALL " +
                    "  SELECT c.ancestor_id, d.id, c.depth + 1 " +
                    "  FROM closure c JOIN sys_dept d ON d.parent_id = c.descendant_id AND d.status = 1 " +
                    "  WHERE c.depth < 20 " +
                    ") SELECT ancestor_id, descendant_id, MAX(depth) FROM closure GROUP BY ancestor_id, descendant_id");
        } catch (Exception e) {
            log.warn("闭包重建失败(表未就绪?): {}", e.getMessage());
        }
    }

    /** 全量任职重建(组织/岗位结构变更后; 事务原子) */
    @org.springframework.transaction.annotation.Transactional
    public void rebuildAllEmployment() {
        try {
            jdbc.update("DELETE FROM sys_employment");
            jdbc.update(
                    "INSERT IGNORE INTO sys_employment (user_id, org_id, position_id, is_primary, status) " +
                    "SELECT up.user_id, p.org_id, p.id, COALESCE(up.is_primary, 1), 1 " +
                    "FROM sys_user_position up " +
                    "JOIN sys_position p ON p.id = up.position_id AND p.org_id IS NOT NULL AND p.status = 1 " +
                    "JOIN sys_user u ON u.id = up.user_id AND u.deleted = 0");
            jdbc.update(
                    "INSERT IGNORE INTO sys_employment (user_id, org_id, position_id, is_primary, status) " +
                    "SELECT u.id, u.dept_id, 0, 1, 1 " +
                    "FROM sys_user u JOIN sys_dept d ON d.id = u.dept_id " +
                    "WHERE u.deleted = 0 " +
                    "AND NOT EXISTS (SELECT 1 FROM sys_employment e WHERE e.user_id = u.id)");
        } catch (Exception e) {
            log.warn("全量任职重建失败(表未就绪?): {}", e.getMessage());
        }
    }
}
