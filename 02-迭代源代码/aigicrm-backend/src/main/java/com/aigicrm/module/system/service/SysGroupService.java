package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.GroupScopeUtil;
import com.aigicrm.module.system.entity.SysGroup;
import com.aigicrm.module.system.mapper.SysGroupMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysGroupService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_SINGLE_GROUP_ONLY = "SINGLE_GROUP_ONLY";


    private final SysGroupMapper groupMapper;

    /**
     * 【11-08 修复清单7】集团管理补集团边界守卫(参照 SysDept/SysRole 同族守卫; 当前单集团属防御性修复):
     * 集团是租户边界的顶层载体, 非 admin 只可见/可操作自己所属集团;
     * 新建集团(=新边界)非 admin 一律拒绝; 无集团归属 fail-closed。
     */
    public List<SysGroup> list() {
        LambdaQueryWrapper<SysGroup> wrapper =
                new LambdaQueryWrapper<SysGroup>().orderByAsc(SysGroup::getSortOrder);
        List<Long> groupIds = GroupScopeUtil.visibleGroupIds();
        // admin → null 不过滤(跨集团管理); 非 admin 无集团归属 → 空集 fail-closed
        if (groupIds != null) {
            if (groupIds.isEmpty()) return List.of();
            wrapper.in(SysGroup::getId, groupIds);
        }
        return groupMapper.selectList(wrapper);
    }

    public SysGroup getById(Long id) {
        SysGroup group = groupMapper.selectById(id);
        if (group != null && !GroupScopeUtil.canAccessGroup(group.getId())) {
            return null; // 非 admin 越权集团返回 null, 防 id 枚举(与 SysRole.getById 同口径)
        }
        return group;
    }

    @Transactional
    public void create(SysGroup group) {
        // 【产品口径 2026-11-15】单集团部署: 仅平台管理员可创建, 且系统不存在任何集团时才允许
        requirePlatformAdmin();
        Long all = groupMapper.selectCount(null);
        if (all != null && all >= 1) {
            throw new BusinessException(400, LIT_SINGLE_GROUP_ONLY, "单集团部署仅允许一个集团");
        }
        Long count = groupMapper.selectCount(
                new LambdaQueryWrapper<SysGroup>().eq(SysGroup::getGroupCode, group.getGroupCode()));
        if (count > 0) throw new BusinessException("集团编码已存在");
        groupMapper.insert(group);
    }

    /** 【产品口径 2026-11-15】集团写操作仅平台管理员(与控制器 @PreAuthorize 双闸) */
    private void requirePlatformAdmin() {
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "ADMIN_ONLY", "仅系统管理员可管理集团");
        }
    }

    @Transactional
    public void update(SysGroup group) {
        requirePlatformAdmin();
        if (group.getId() == null) throw new BusinessException(400, "集团ID不能为空");
        SysGroup existing = groupMapper.selectById(group.getId());
        if (existing == null) throw new BusinessException(404, "集团不存在");
        // 集团编码创建后不可修改(前端已禁改, 后端兜底)
        if (group.getGroupCode() != null && !group.getGroupCode().equals(existing.getGroupCode())) {
            throw new BusinessException(400, "集团编码创建后不可修改");
        }
        // 单活跃集团不可禁用(与 delete 守卫同口径, 防止经 update 绕过)
        if (group.getStatus() != null && group.getStatus() == 0) {
            Long active = groupMapper.selectCount(
                    new LambdaQueryWrapper<SysGroup>().eq(SysGroup::getStatus, 1));
            if (active != null && active <= 1) {
                throw new BusinessException(400, LIT_SINGLE_GROUP_ONLY, "不能禁用唯一集团");
            }
        }
        groupMapper.updateById(group);
    }

    @Transactional
    public void delete(Long id) {
        requirePlatformAdmin();
        if (!GroupScopeUtil.canAccessGroup(id)) {
            throw new BusinessException(403, "无权操作其他集团");
        }
        // 【产品口径 2026-11-15】单集团不可禁用/删除。
        // 集团是租户边界的顶层载体, 禁用唯一活跃集团会让全部数据失去归属(不可恢复的运维事故);
        // 本方法是软禁用(status=0), 因此守卫只拦"最后一个活跃集团", 已禁用集团的清理仍放行。
        SysGroup existing = groupMapper.selectById(id);
        if (existing != null && existing.getStatus() != null && existing.getStatus() == 1) {
            Long active = groupMapper.selectCount(
                    new LambdaQueryWrapper<SysGroup>().eq(SysGroup::getStatus, 1));
            if (active != null && active <= 1) {
                throw new BusinessException(400, LIT_SINGLE_GROUP_ONLY, "不能删除唯一集团");
            }
        }
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setStatus(0);
        groupMapper.updateById(group);
    }
}
