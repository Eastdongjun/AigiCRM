package com.ycmn.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 六级数据范围统一解析器（设计文档 §4.2）
 * 输出可见用户集合 U：null=本集团不过滤(all)；空=仅本人；其余=可见 userIds（恒含本人）
 */
@Component
@RequiredArgsConstructor
public class OrgScopeResolver {

    /** 团队递归上限（manager_id 链） */
    public static final int TEAM_MAX_DEPTH = 5;

    /** 大区/办事处 deptType 标记值（与组织管理页字典一致） */
    public static final int TYPE_COMPANY = 1;
    public static final int TYPE_REGION = 2;
    public static final int TYPE_OFFICE = 3;
    public static final int TYPE_DEPT = 4;

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    /** 解析当前用户可见 userIds（恒含本人；all 返回 null） */
    public List<Long> resolve(SecurityUser user) {
        if (user == null) return List.of();
        String scope = user.getDataScope() != null ? user.getDataScope() : "self";
        Long userId = user.getUserId();
        Long deptId = user.getDeptId() != null ? user.getDeptId() : user.getOfficeId();

        switch (scope) {
            case "all": return null;
            case "custom": return resolveCustom(user, userId);
            case "region": return resolveByAncestorType(user, userId, deptId, TYPE_REGION, true);
            case "office": return resolveByAncestorType(user, userId, deptId, TYPE_OFFICE, false);
            case "dept": return resolveDeptTree(user, userId, deptId, false);
            case "team": return resolveTeam(user, userId);
            case "self":
            default: return List.of(userId);
        }
    }

    /** 可见部门集合（scope 接口用）：U 中用户所属 deptId 去重 + 本人部门 */
    public List<Long> resolveVisibleDeptIds(SecurityUser user) {
        List<Long> u = resolve(user);
        if (u == null) return null; // all：不过滤
        Set<Long> deptIds = new LinkedHashSet<>();
        if (!u.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(u);
            for (SysUser su : users) {
                if (su.getDeptId() != null) deptIds.add(su.getDeptId());
            }
        }
        if (user.getDeptId() != null) deptIds.add(user.getDeptId());
        return new ArrayList<>(deptIds);
    }

    // ---- 各级别实现 ----

    private List<Long> resolveTeam(SecurityUser user, Long userId) {
        Set<Long> result = new LinkedHashSet<>();
        result.add(userId);
        List<Long> currentLevel = List.of(userId);
        for (int depth = 0; depth < TEAM_MAX_DEPTH && !currentLevel.isEmpty(); depth++) {
            List<Long> subs = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getManagerId, currentLevel)
                            .eq(SysUser::getDeleted, 0))
                    .stream().map(SysUser::getId).toList();
            List<Long> fresh = subs.stream().filter(id -> result.add(id)).toList();
            currentLevel = fresh;
        }
        return new ArrayList<>(result);
    }

    private List<Long> resolveDeptTree(SecurityUser user, Long userId, Long deptId, boolean includeSelfTree) {
        if (deptId == null) return List.of(userId);
        Set<Long> deptIds = collectSubtree(deptId, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private List<Long> resolveByAncestorType(SecurityUser user, Long userId, Long deptId, int targetType, boolean fallbackToRoot) {
        if (deptId == null) return List.of(userId);
        Long anchor = findAncestorOfType(deptId, targetType);
        if (anchor == null) {
            // 无标记：office 退化 dept 树；region 退化集团根（沿祖先链到 parent 为 null 的根）
            return targetType == TYPE_OFFICE
                    ? resolveDeptTree(user, userId, deptId, false)
                    : resolveDeptTree(user, userId, findRoot(deptId), false);
        }
        Set<Long> deptIds = collectSubtree(anchor, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private List<Long> resolveCustom(SecurityUser user, Long userId) {
        List<Long> managed = user.getManagedOrgIds();
        if (managed == null || managed.isEmpty()) return List.of(userId);
        Set<Long> deptIds = new LinkedHashSet<>();
        for (Long orgId : managed) deptIds.addAll(collectSubtree(orgId, null));
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private Set<Long> collectSubtree(Long rootId, Set<Long> acc) {
        if (acc == null) acc = new LinkedHashSet<>();
        acc.add(rootId);
        List<Long> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getParentId, rootId)
                        .eq(SysDept::getStatus, 1))
                .stream().map(SysDept::getId).toList();
        // 环保护：已收集过的节点不再递归，防止 parent_id 环数据死循环
        for (Long c : children) {
            if (acc.add(c)) collectSubtree(c, acc);
        }
        return acc;
    }

    private Long findAncestorOfType(Long deptId, int type) {
        Long cur = deptId;
        for (int i = 0; i < 20 && cur != null; i++) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null) return null;
            if (d.getDeptType() != null && d.getDeptType() == type) return d.getId();
            cur = d.getParentId();
        }
        return null;
    }

    private Long findRoot(Long deptId) {
        Long cur = deptId;
        for (int i = 0; i < 20 && cur != null; i++) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null || d.getParentId() == null) return cur;
            cur = d.getParentId();
        }
        return cur;
    }

    private List<Long> usersInDepts(Set<Long> deptIds) {
        if (deptIds.isEmpty()) return new ArrayList<>();
        // 注意：Stream.toList() 返回不可变列表，调用方需要 add(本人)，必须包一层可变 ArrayList
        return new ArrayList<>(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, deptIds)
                        .eq(SysUser::getDeleted, 0)
                        .eq(SysUser::getStatus, 1))
                .stream().map(SysUser::getId).toList());
    }
}
