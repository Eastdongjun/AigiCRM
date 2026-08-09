package com.ycmn.module.sea.service;

import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 公海专项权限（2026-08-10 按组织架构层级勾选）。
 *
 * 四项独立：view/edit/delete/claim，每项授权对象 = 组织节点集(dept_ids) + 人员集(user_ids)，
 * 勾选上级组织含下级递归。专项为准（存量角色码 sea:claim/sea:pool:edit/sea:pool:delete 失效）。
 * admin 恒有全部权限（不走表判定）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeaPermissionService {

    public static final String VIEW = "view";
    public static final String EDIT = "edit";
    public static final String DELETE = "delete";
    public static final String CLAIM = "claim";

    private final JdbcTemplate jdbc;
    private final com.ycmn.module.system.mapper.SysDeptMapper deptMapper;
    private final com.ycmn.module.system.mapper.SysUserMapper userMapper;

    /**
     * 用户是否持某公海专项权限（admin 恒 true）。
     * ⚠️ admin 短路基于"当前登录会话"判定——本方法仅支持传当前用户 userId（内部调用方均如此），
     *    勿用于跨用户判定（登录者为 admin 时会恒 true）。
     */
    public boolean hasPerm(String permType, Long userId) {
        if (userId == null) return false;
        if (isAdmin()) return true;
        return hasPermForUser(permType, userId);
    }

    /** 当前用户是否持某权限（供 Controller 方法内用当前登录用户判定） */
    public boolean currentUserHasPerm(String permType) {
        return hasPerm(permType, SecurityUtils.getCurrentUserId());
    }

    /**
     * 查询用户的部门归属（含上级链递归匹配 dept_ids），或直接命中 user_ids。
     * 复用 SeaConfigService.isVisibleTo 的组织树递归逻辑：用户部门向上追溯，命中授权组织即放行。
     */
    private boolean hasPermForUser(String permType, Long userId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("SELECT dept_ids, user_ids FROM biz_sea_permission WHERE perm_type=?", permType);
        } catch (Exception e) {
            return false; // 无授权行 → 无权限
        }
        String deptIdsCsv = row.get("dept_ids") != null ? String.valueOf(row.get("dept_ids")) : null;
        String userIdsCsv = row.get("user_ids") != null ? String.valueOf(row.get("user_ids")) : null;

        // 1. 人员直接命中
        if (userIdsCsv != null && csvContains(userIdsCsv, userId)) return true;

        // 2. 部门/上级链递归命中
        if (!StringUtils.hasText(deptIdsCsv)) return false;
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getDeptId() == null) return false;

        Set<Long> authorizedDepts = parseIds(deptIdsCsv).stream().collect(Collectors.toSet());
        Map<Long, SysDept> deptMap = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        SysDept cur = deptMap.get(user.getDeptId());
        int guard = 0;
        while (cur != null && guard++ < 20) {
            if (authorizedDepts.contains(cur.getId())) return true;
            cur = cur.getParentId() != null ? deptMap.get(cur.getParentId()) : null;
        }
        return false;
    }

    private boolean isAdmin() {
        return SecurityUtils.isAdmin();
    }

    private boolean csvContains(String csv, Long id) {
        if (!StringUtils.hasText(csv) || id == null) return false;
        String target = String.valueOf(id);
        return Arrays.stream(csv.split(",")).map(String::trim).anyMatch(target::equals);
    }

    private List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong).collect(Collectors.toList());
    }
}
