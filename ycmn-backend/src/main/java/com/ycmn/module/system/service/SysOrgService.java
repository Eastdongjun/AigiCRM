package com.ycmn.module.system.service;
import com.ycmn.security.SecurityUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysOrg;
import com.ycmn.module.system.entity.SysOrgAdmin;
import com.ycmn.module.system.mapper.SysOrgAdminMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysOrgService {

    private final SysOrgMapper orgMapper;
    private final SysOrgAdminMapper orgAdminMapper;
    private final SysUserMapper userMapper;

    /** 获取组织树 */
    public List<SysOrg> getOrgTree() {
        List<SysOrg> all = orgMapper.selectList(
                new LambdaQueryWrapper<SysOrg>()
                        .eq(SysOrg::getStatus, 1)
                        .orderByAsc(SysOrg::getSortOrder));
        return buildTree(all, 0L);
    }

    private List<SysOrg> buildTree(List<SysOrg> all, Long parentId) {
        List<SysOrg> result = new ArrayList<>();
        for (SysOrg org : all) {
            if (Objects.equals(org.getParentId(), parentId)) {
                org.setChildren(buildTree(all, org.getId()));
                result.add(org);
            }
        }
        return result;
    }

    /** 创建组织 */
    @Transactional
    public SysOrg create(SysOrg org) {
        orgMapper.insert(org);
        return org;
    }

    /** 更新组织 */
    @Transactional
    public void update(SysOrg org) {
        orgMapper.updateById(org);
    }

    /** 删除组织（软删除） */
    @Transactional
    public void delete(Long id) {
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setStatus(0);
        orgMapper.updateById(org);
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
        com.ycmn.module.system.entity.SysUser target = userMapper.selectById(targetUserId);
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
        java.util.List<Long> orgIds = SecurityUtils.getCurrentManagedOrgIds();
        return userMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ycmn.module.system.entity.SysUser>()
                .in(com.ycmn.module.system.entity.SysUser::getOrgId, orgIds)
                .eq(com.ycmn.module.system.entity.SysUser::getStatus, 1)
                .eq(com.ycmn.module.system.entity.SysUser::getDeleted, 0)
        ).stream().map(com.ycmn.module.system.entity.SysUser::getId).collect(java.util.stream.Collectors.toList());
    }
}
