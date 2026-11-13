package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.security.SecurityUtils;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final com.aigicrm.security.PermissionCacheService permissionCacheService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 【2026-08-14 缓存自愈】全局权限版本递增：部门变更影响所有用户缓存的派生值，evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈 */
    private void bumpAllPermVersions() {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1");
    }

    public List<SysDept> tree() {
        List<SysDept> all = scopeFilter(deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder)
        ));
        // 填充负责人名称
        for (SysDept dept : all) {
            if (dept.getLeaderId() != null) {
                SysUser leader = userMapper.selectById(dept.getLeaderId());
                if (leader != null) dept.setLeaderName(leader.getRealName());
            }
        }
        return buildTree(all, 0L);
    }

    public List<SysDept> list() {
        return scopeFilter(deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder)
        ));
    }

    // 【N-06 2026-09-19】原 treeAll()("全量组织树,不套数据范围过滤")已删除:SysOrgController
    // 迁移后无任何调用方,是带不安全语义的死代码——任何未来的"全量组织树"需求必须
    // 走管理员权限并显式过审,不得复活本方法。

    /** 【需求】部门查询数据范围收窄(FEAT-20260804-DEPTS-DATASCOPE)
     *  【方法】管理员返回全部;非管理员仅返回本人部门及其祖先链(按 parentId 上溯至根),
     *          与销售员 self 数据范围一致,避免组织架构全量可见
     *  【N-06 2026-09-19 集团边界加固】祖先链之外再按用户所属集团收窄:防御脏数据下
     *          parent 链跨集团时把别集团部门带出(与 PR-01 家族 sys_user 口径一致,fail-closed:
     *          无集团归属返回空)。
     *  【参数】all 全量启用部门
     *  【返回】过滤后的部门列表;dept_id 为空时返回空列表 */
    private List<SysDept> scopeFilter(List<SysDept> all) {
        if (SecurityUtils.isAdmin()) {
            return all;
        }
        Long myDeptId = SecurityUtils.getCurrentDeptId();
        if (myDeptId == null) {
            return Collections.emptyList();
        }
        List<Long> groupIds = com.aigicrm.common.scope.GroupScopeUtil.visibleGroupIds();
        if (groupIds != null && !groupIds.isEmpty()) {
            Set<Long> groups = new HashSet<>(groupIds);
            all = all.stream().filter(d -> d.getGroupId() != null && groups.contains(d.getGroupId()))
                    .collect(Collectors.toList());
        } else {
            // 无集团归属:fail-closed,不退回任何部门
            return Collections.emptyList();
        }
        Map<Long, SysDept> byId = all.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Set<Long> keep = new HashSet<>();
        Long cur = myDeptId;
        while (cur != null && byId.containsKey(cur)) {
            keep.add(cur);
            cur = byId.get(cur).getParentId();
        }
        return all.stream().filter(d -> keep.contains(d.getId())).collect(Collectors.toList());
    }

    public void create(SysDept dept) {
        // 【P1 2026-09-29 复审修复·集团边界】与 SysPosition 家族(REQ-005)同口径:
        // 未传 groupId 兜底当前集团(无集团 fail-closed,不默认建到主集团——P0-02);
        // 显式指定越权集团拒绝。读路径已收窄(N-06),写路径此前只看权限码。
        if (dept.getGroupId() == null) {
            List<Long> g = com.aigicrm.common.scope.GroupScopeUtil.visibleGroupIds();
            if (g != null && g.isEmpty()) throw new BusinessException("当前账号无有效集团归属，无法创建部门");
            dept.setGroupId(g == null ? 1L : g.get(0));
        } else if (!com.aigicrm.common.scope.GroupScopeUtil.canAccessGroup(dept.getGroupId())) {
            throw new BusinessException(403, "无权在该集团创建部门");
        }
        deptMapper.insert(dept);
        // 【P1-1 三轮修复 2026-11-13】新建部门 → 闭包/任职全量重建+epoch(evictAll 汇聚),
        // 否则新部门不在 sys_org_closure, 新轨 MY_DEPT_SUB 漏新部门
        permissionCacheService.evictAll();
        bumpAllPermVersions();
    }

    public void update(SysDept dept) {
        // 【P1 2026-09-29 复审修复·集团边界】三向校验(与 SysPosition.update 同口径):
        // 目标部门越权拒;新归属越权拒;新父节点越权拒(脏数据防线:parent 跨集团=变相迁移)。
        if (dept.getId() != null) {
            SysDept existing = deptMapper.selectById(dept.getId());
            if (existing != null && !com.aigicrm.common.scope.GroupScopeUtil.canAccessGroup(existing.getGroupId())) {
                throw new BusinessException(403, "无权操作其他集团的部门");
            }
        }
        if (dept.getGroupId() != null && !com.aigicrm.common.scope.GroupScopeUtil.canAccessGroup(dept.getGroupId())) {
            throw new BusinessException(403, "无权将部门归属到其他集团");
        }
        if (dept.getParentId() != null) {
            SysDept parent = deptMapper.selectById(dept.getParentId());
            if (parent != null && !com.aigicrm.common.scope.GroupScopeUtil.canAccessGroup(parent.getGroupId())) {
                throw new BusinessException(403, "无权将部门挂到其他集团的部门下");
            }
        }
        deptMapper.updateById(dept);
        // 【Redis 权限缓存】部门 groupId/legalEntityId/层级变更影响用户缓存的派生值(集团/法人/范围)，
        // 全量失效(低频操作)避免旧上下文残留。
        permissionCacheService.evictAll();
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpAllPermVersions();
    }

    public void delete(Long id) {
        // 【P1 2026-09-29 复审修复·集团边界】目标部门越权先于子部门/用户检查拒绝(与读路径同口径)
        SysDept target = deptMapper.selectById(id);
        if (target != null && !com.aigicrm.common.scope.GroupScopeUtil.canAccessGroup(target.getGroupId())) {
            throw new BusinessException(403, "无权删除其他集团的部门");
        }
        // 存在子部门时不允许删除，避免产生孤立节点
        Long childCount = deptMapper.selectCount(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该部门下存在子部门，请先删除或转移子部门");
        }
        // 部门下存在用户时不允许删除（selectCount 会自动过滤已逻辑删除的用户）
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw new BusinessException("该部门下存在用户，请先转移用户后再删除");
        }
        deptMapper.deleteById(id);
        // 【P1-1 三轮修复 2026-11-13】删除部门 → 闭包重建+失效(同 create 口径)
        permissionCacheService.evictAll();
        bumpAllPermVersions();
    }

    /** 软删除（置 status=0，保留记录）——/api/org 兼容层使用，语义=旧 SysOrgService.delete */
    public void softDelete(Long id) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setStatus(0);
        deptMapper.updateById(dept);
        // 【P1-1 三轮修复】status=0 改变闭包/树有效性 → 同 hard delete 口径失效+重建
        permissionCacheService.evictAll();
        bumpAllPermVersions();
    }

    private List<SysDept> buildTree(List<SysDept> list, Long parentId) {
        return list.stream()
                .filter(d -> Objects.equals(d.getParentId(), parentId))
                .peek(d -> d.setChildren(buildTree(list, d.getId())))
                .collect(Collectors.toList());
    }
}
