package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysRoleMenu;
import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysRoleMenuMapper;
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

    public List<SysRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortOrder));
    }

    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
            role.setMenuIds(menuMapper.selectMenuIdsByRoleId(id));
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
    }

    @Transactional
    public void update(SysRole role) {
        // 仅当有实际字段需要更新时才执行 updateById，否则 MyBatis-Plus 会生成
        // "UPDATE sys_role WHERE id=?" 空 SET 子句导致 SQL 语法错误（如仅分配权限只传 menuIds）
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
    }

    @Transactional
    public void delete(Long id) {
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
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
}
