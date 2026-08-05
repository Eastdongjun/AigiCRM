package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.common.scope.GroupScopeUtil;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysRoleMenu;
import com.ycmn.module.system.entity.SysRoleOrg;
import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysRoleMenuMapper;
import com.ycmn.module.system.mapper.SysRoleOrgMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleOrgMapper roleOrgMapper;

    /**
     * 角色列表（按集团边界过滤）。
     * 【修复 角色列表集团边界】非 admin 仅见本集团角色;admin 全量。
     * 口径见 {@link GroupScopeUtil}（与人员列表 applyGroupScope 一致）。
     */
    public List<SysRole> list() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortOrder);
        List<Long> groupIds = GroupScopeUtil.visibleGroupIds();
        if (groupIds != null) {
            wrapper.in(SysRole::getGroupId, groupIds);
        }
        return roleMapper.selectList(wrapper);
    }

    /** 角色详情（含菜单权限与 custom 数据范围的组织集合；非 admin 越权集团返回 null，防 id 枚举） */
    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
            // 【修复 角色详情集团边界】与列表同口径：非 admin 无权读取其他集团角色
            if (!GroupScopeUtil.canAccessGroup(role.getGroupId())) {
                return null;
            }
            role.setMenuIds(menuMapper.selectMenuIdsByRoleId(id));
            // 如果是 custom 类型，加载组织关联
            if (DataScopeType.CUSTOM.getCode().equals(role.getDataScope())) {
                role.setOrgIds(roleOrgMapper.selectByRoleId(id).stream()
                        .map(SysRoleOrg::getOrgId)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
        return role;
    }

    /** 新增角色（编码唯一校验 + 菜单/组织关联保存；集团归属兜底，越权集团拒绝） */
    @Transactional
    public void create(SysRole role) {
        // 【修复 角色创建集团边界】未传 groupId 时显式兜底：非 admin=当前用户集团，admin=主集团 1；
        // 传了但越权（非 admin 建到其他集团）→ 拒绝
        if (role.getGroupId() == null) {
            List<Long> g = GroupScopeUtil.visibleGroupIds();
            role.setGroupId(g == null ? 1L : g.get(0));
        } else if (!GroupScopeUtil.canAccessGroup(role.getGroupId())) {
            throw new BusinessException("无权在该集团创建角色");
        }
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.getRoleCode())
        );
        if (count > 0) throw new BusinessException("角色编码已存在");
        roleMapper.insert(role);
        saveRoleMenus(role.getId(), role.getMenuIds());
        saveRoleOrgs(role);
    }

    /** 更新角色（有实际字段才更新；菜单/dataScope 变更时同步重建关联；越权集团拒绝） */
    @Transactional
    public void update(SysRole role) {
        // 【修复 角色更新集团边界】目标角色越权不可操作；新 groupId 越权不可归属
        if (role.getId() != null) {
            SysRole existing = roleMapper.selectById(role.getId());
            if (existing != null && !GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
                throw new BusinessException("无权操作其他集团的角色");
            }
        }
        if (role.getGroupId() != null && !GroupScopeUtil.canAccessGroup(role.getGroupId())) {
            throw new BusinessException("无权将角色归属到其他集团");
        }
        // 仅当有实际字段需要更新时才执行 updateById
        boolean hasRoleFields = role.getRoleName() != null || role.getRoleCode() != null
                || role.getDescription() != null || role.getDataScope() != null
                || role.getSortOrder() != null || role.getStatus() != null
                || role.getGroupId() != null;
        if (hasRoleFields) {
            roleMapper.updateById(role);
        }
        if (role.getMenuIds() != null) {
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()));
            saveRoleMenus(role.getId(), role.getMenuIds());
        }
        // 如果传了 dataScope，同步更新组织关联
        if (role.getDataScope() != null) {
            saveRoleOrgs(role);
        }
    }

    /**
     * 更新角色-组织数据范围关联
     *
     * 【需求】角色管理页面维护 custom 数据范围的组织集合
     * 【方法】清空后按 orgIds 重建关联（saveRoleOrgs 幂等）
     * 【业务规则】仅 custom 数据范围生效；orgIds 为空则清空
     * 【参数】roleId 角色ID；orgIds 选中组织ID集合
     * 【注意】调用方需有 system:role:edit 权限
     */
    @Transactional
    public void updateOrgs(Long roleId, List<Long> orgIds) {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setDataScope(DataScopeType.CUSTOM.getCode());
        role.setOrgIds(orgIds);
        saveRoleOrgs(role);
    }

    /** 删除角色（连带清理菜单/组织关联；越权集团拒绝） */
    @Transactional
    public void delete(Long id) {
        // 【修复 角色删除集团边界】非 admin 无权删除其他集团角色
        SysRole existing = roleMapper.selectById(id);
        if (existing != null && !GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
            throw new BusinessException("无权删除其他集团的角色");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleOrgMapper.deleteByRoleId(id);
    }

    private void saveRoleMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) return;
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    /**
     * 保存角色-组织关联（custom 类型时生效）
     * 非 custom 类型时清空所有关联
     */
    private void saveRoleOrgs(SysRole role) {
        // 先清空
        roleOrgMapper.deleteByRoleId(role.getId());

        // 非 custom 类型不保存组织关联
        if (!DataScopeType.CUSTOM.getCode().equals(role.getDataScope())) {
            return;
        }

        // custom 类型：保存选中的组织
        List<Long> orgIds = role.getOrgIds();
        if (orgIds == null || orgIds.isEmpty()) return;

        for (Long orgId : orgIds) {
            SysRoleOrg ro = new SysRoleOrg();
            ro.setRoleId(role.getId());
            ro.setOrgId(orgId);
            ro.setIncludeChildren(1); // 默认含下级，前端可传具体值
            roleOrgMapper.insert(ro);
        }
    }
}
