package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysMenu;
import com.ycmn.module.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 菜单服务(2026-08-13 起只读): 菜单 CRUD 端点已随孤儿端点清理移除,
 * 菜单数据维护走 SQL 种子迁移(init.sql / V2 / V12 / V14), 与权限矩阵解耦。
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;

    public List<SysMenu> tree() {
        List<SysMenu> all = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1).orderByAsc(SysMenu::getSortOrder)
        );
        return buildTree(all, 0L);
    }

    private List<SysMenu> buildTree(List<SysMenu> list, Long parentId) {
        return list.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .peek(m -> m.setChildren(buildTree(list, m.getId())))
                .collect(Collectors.toList());
    }
}
