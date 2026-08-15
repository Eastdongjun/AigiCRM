package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysOrgAdmin;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysOrgAdminMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织管理员服务（sys_org_admin）。
 * 自旧 SysOrgService 迁移：只保留组织管理员相关方法，组织树 CRUD 已并入 SysDeptService。
 * 守卫逻辑、去重、返回结构与原实现完全一致。
 */
@Service
@RequiredArgsConstructor
public class OrgAdminService {

    private final SysOrgAdminMapper orgAdminMapper;
    private final SysUserMapper userMapper;
    private final com.ycmn.security.PermissionCacheService permissionCacheService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 【2026-08-14 缓存自愈】用户级权限版本递增：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈 */
    private void bumpPermVersion(Long userId) {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 WHERE id=?", userId);
    }

    /** 获取组织管理员列表 */
    public List<Map<String, Object>> getOrgAdmins(Long orgId) {
        List<SysOrgAdmin> admins = orgAdminMapper.selectList(
                new LambdaQueryWrapper<SysOrgAdmin>().eq(SysOrgAdmin::getOrgId, orgId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysOrgAdmin admin : admins) {
            var user = userMapper.selectById(admin.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", admin.getId());
            m.put("orgId", admin.getOrgId());
            m.put("userId", admin.getUserId());
            m.put("userName", user != null ? user.getRealName() : "");
            result.add(m);
        }
        return result;
    }

    /** 添加组织管理员 */
    @Transactional
    public void addOrgAdmin(Long orgId, Long userId) {
        // 【安全 2026-08-08】仅能在调用者管辖的组织内任命管理员
        if (!canManageOrg(orgId)) {
            throw new com.ycmn.common.BusinessException("无权在该组织任命管理员");
        }
        // 检查是否已存在
        Long count = orgAdminMapper.selectCount(
                new LambdaQueryWrapper<SysOrgAdmin>()
                        .eq(SysOrgAdmin::getOrgId, orgId)
                        .eq(SysOrgAdmin::getUserId, userId));
        if (count > 0) return;

        SysOrgAdmin admin = new SysOrgAdmin();
        admin.setOrgId(orgId);
        admin.setUserId(userId);
        orgAdminMapper.insert(admin);
        // 【Redis 权限缓存】组织管理员变更影响该用户 managedOrgIds/dataScope 升级，立即失效
        permissionCacheService.evict(userId);
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpPermVersion(userId);
    }

    /** 移除组织管理员 */
    @Transactional
    public void removeOrgAdmin(Long id) {
        // 【安全 2026-08-08】移除前校验被移除记录的 org 在调用者管辖范围内
        SysOrgAdmin existing = orgAdminMapper.selectById(id);
        if (existing == null) return;
        if (!canManageOrg(existing.getOrgId())) {
            throw new com.ycmn.common.BusinessException("无权移除该组织的管理员");
        }
        orgAdminMapper.deleteById(id);
        // 【Redis 权限缓存】组织管理员移除立即失效该用户
        permissionCacheService.evict(existing.getUserId());
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpPermVersion(existing.getUserId());
    }

    /** 获取用户管理的组织ID列表（含下级） */
    public List<Long> getManagedOrgIds(Long userId) {
        return orgAdminMapper.selectManagedOrgIds(userId);
    }

    /** 检查当前用户是否能管理目标用户 */
    public boolean canManageUser(Long targetUserId) {
        if (SecurityUtils.isAdmin()) return true;
        if (!SecurityUtils.isOrgAdmin()) return false;
        // 【2026-08-15 安全审计 H1】组织管理员不得操作 admin 角色账号(重置密码拿临时口令/删除即接管全系统),
        // 角色硬校验放在组织范围之前——与目标是否在本组织无关, 任何 admin 角色账号一律拒绝
        if (hasAdminRole(targetUserId)) return false;
        SysUser target = userMapper.selectById(targetUserId);
        if (target == null || target.getOrgId() == null) return false;
        return SecurityUtils.getCurrentManagedOrgIds().contains(target.getOrgId());
    }

    /** 目标用户是否持有 admin 角色(角色码 sys_role.role_code='admin', 经 sys_user_role 关联查询) */
    private boolean hasAdminRole(Long userId) {
        if (userId == null) return false;
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id " +
                        "WHERE ur.user_id = ? AND r.role_code = 'admin'", Integer.class, userId);
        return cnt != null && cnt > 0;
    }

    /** 检查当前用户是否能管理目标组织 */
    public boolean canManageOrg(Long targetOrgId) {
        if (SecurityUtils.isAdmin()) return true;
        if (!SecurityUtils.isOrgAdmin()) return false;
        return SecurityUtils.getCurrentManagedOrgIds().contains(targetOrgId);
    }

    /** 获取当前用户可管理的用户ID列表（空=全部） */
    public java.util.List<Long> getManageableUserIds() {
        if (SecurityUtils.isAdmin()) return java.util.Collections.emptyList();
        if (!SecurityUtils.isOrgAdmin()) return java.util.List.of(SecurityUtils.getCurrentUserId());
        List<Long> orgIds = SecurityUtils.getCurrentManagedOrgIds();
        return userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getOrgId, orgIds)
                        .eq(SysUser::getStatus, 1)
                        .eq(SysUser::getDeleted, 0)
        ).stream().map(SysUser::getId).collect(Collectors.toList());
    }
}
