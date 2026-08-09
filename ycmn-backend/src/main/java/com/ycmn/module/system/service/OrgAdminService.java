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
    }

    /** 获取用户管理的组织ID列表（含下级） */
    public List<Long> getManagedOrgIds(Long userId) {
        return orgAdminMapper.selectManagedOrgIds(userId);
    }

    /** 检查当前用户是否能管理目标用户 */
    public boolean canManageUser(Long targetUserId) {
        if (SecurityUtils.isAdmin()) return true;
        if (!SecurityUtils.isOrgAdmin()) return false;
        SysUser target = userMapper.selectById(targetUserId);
        if (target == null || target.getOrgId() == null) return false;
        return SecurityUtils.getCurrentManagedOrgIds().contains(target.getOrgId());
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
