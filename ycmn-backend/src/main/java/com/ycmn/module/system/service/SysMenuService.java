package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysMenu;
import com.ycmn.module.system.entity.SysRoleMenu;
import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final com.ycmn.module.system.mapper.SysRoleMenuMapper roleMenuMapper;
    private final com.ycmn.security.PermissionCacheService permissionCacheService;

    public List<SysMenu> tree() {
        List<SysMenu> all = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1).orderByAsc(SysMenu::getSortOrder)
        );
        return buildTree(all, 0L);
    }

    public void create(SysMenu menu) {
        menuMapper.insert(menu);
        // 【Redis 权限缓存】菜单定义影响所有用户权限集合，全量失效（低频操作）
        permissionCacheService.evictAll();
    }

    public void update(SysMenu menu) {
        menuMapper.updateById(menu);
        permissionCacheService.evictAll();
    }

    public void delete(Long id) {
        menuMapper.deleteById(id);
        // 【修复 2026-08-10 P2-08】删除菜单连带清理 role_menu 绑定(与 V2 迁移"先解绑再删菜单"同模式),
        // 避免孤儿绑定残留并在菜单 ID 复用场景复活权限
        roleMenuMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getMenuId, id));
        permissionCacheService.evictAll();
    }

    private List<SysMenu> buildTree(List<SysMenu> list, Long parentId) {
        return list.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .peek(m -> m.setChildren(buildTree(list, m.getId())))
                .collect(Collectors.toList());
    }
}
