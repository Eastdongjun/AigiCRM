package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysMenu;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysRoleMenu;
import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final com.ycmn.module.system.mapper.SysRoleMenuMapper roleMenuMapper;
    private final com.ycmn.security.PermissionCacheService permissionCacheService;
    private final SysRoleMapper roleMapper;

    public List<SysMenu> tree() {
        List<SysMenu> all = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1).orderByAsc(SysMenu::getSortOrder)
        );
        return buildTree(all, 0L);
    }

    /** 新菜单自动绑定 admin 角色(按 role_code='admin' 解析 id, 不硬编码 role_id=1) */
    @Transactional
    public void create(SysMenu menu) {
        menuMapper.insert(menu);
        // 【安全 2026-08-11 P1 修复】此前 admin 的全量绑定只在 init.sql 初始化时一次性建立,
        // 运行时新增菜单(新权限码/新按钮)不会绑定到 admin → 新接口 @PreAuthorize 码对 admin 也 403(缺授)。
        // 目录行(menu_type=1)无权限码, 仍补绑以保证 admin 侧边栏完整。
        // 按 role_code 定位 admin 角色, 避免硬编码 id=1 在异构部署下把新权限误授给 id=1 的非 admin 角色。
        if (menu.getId() != null) {
            SysRole admin = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "admin").last("LIMIT 1"));
            if (admin != null && admin.getId() != null) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(admin.getId());
                rm.setMenuId(menu.getId());
                roleMenuMapper.insert(rm);
            }
        }
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
