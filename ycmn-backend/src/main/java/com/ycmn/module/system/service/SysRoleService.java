package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.enums.DataScopeType;
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

    public List<SysRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortOrder));
    }

    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
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

    @Transactional
    public void create(SysRole role) {
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.getRoleCode())
        );
        if (count > 0) throw new BusinessException("角色编码已存在");
        roleMapper.insert(role);
        saveRoleMenus(role.getId(), role.getMenuIds());
        saveRoleOrgs(role);
    }

    @Transactional
    public void update(SysRole role) {
        // 仅当有实际字段需要更新时才执行 updateById
        boolean hasRoleFields = role.getRoleName() != null || role.getRoleCode() != null
                || role.getDescription() != null || role.getDataScope() != null
                || role.getSortOrder() != null || role.getStatus() != null;
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

    @Transactional
    public void delete(Long id) {
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
