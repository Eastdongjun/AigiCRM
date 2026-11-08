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
        // 新建集团=定义新边界, 必然在操作人所属集团之外——非 admin 拒绝
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException("仅平台管理员可创建集团");
        }
        Long count = groupMapper.selectCount(
                new LambdaQueryWrapper<SysGroup>().eq(SysGroup::getGroupCode, group.getGroupCode()));
        if (count > 0) throw new BusinessException("集团编码已存在");
        groupMapper.insert(group);
    }

    @Transactional
    public void update(SysGroup group) {
        // 目标集团越权拒(与 SysDept.update 的"目标部门越权拒"同口径)
        if (group.getId() != null) {
            SysGroup existing = groupMapper.selectById(group.getId());
            if (existing != null && !GroupScopeUtil.canAccessGroup(existing.getId())) {
                throw new BusinessException("无权操作其他集团");
            }
        }
        groupMapper.updateById(group);
    }

    @Transactional
    public void delete(Long id) {
        if (!GroupScopeUtil.canAccessGroup(id)) {
            throw new BusinessException("无权操作其他集团");
        }
        SysGroup group = new SysGroup();
        group.setId(id);
        group.setStatus(0);
        groupMapper.updateById(group);
    }
}
