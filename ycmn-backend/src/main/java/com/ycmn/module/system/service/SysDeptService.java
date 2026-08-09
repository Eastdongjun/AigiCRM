package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.security.SecurityUtils;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final com.ycmn.security.PermissionCacheService permissionCacheService;

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

    /** 全量组织树（不套数据范围过滤）——/api/org 兼容层使用，语义=旧 SysOrgService.getOrgTree */
    public List<SysDept> treeAll() {
        return buildTree(deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder)
        ), 0L);
    }

    /** 【需求】部门查询数据范围收窄(FEAT-20260804-DEPTS-DATASCOPE)
     *  【方法】管理员返回全部;非管理员仅返回本人部门及其祖先链(按 parentId 上溯至根),
     *          与销售员 self 数据范围一致,避免组织架构全量可见
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
        deptMapper.insert(dept);
    }

    public void update(SysDept dept) {
        deptMapper.updateById(dept);
        // 【Redis 权限缓存】部门 groupId/legalEntityId/层级变更影响用户缓存的派生值(集团/法人/范围)，
        // 全量失效(低频操作)避免旧上下文残留。
        permissionCacheService.evictAll();
    }

    public void delete(Long id) {
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
    }

    /** 软删除（置 status=0，保留记录）——/api/org 兼容层使用，语义=旧 SysOrgService.delete */
    public void softDelete(Long id) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setStatus(0);
        deptMapper.updateById(dept);
    }

    private List<SysDept> buildTree(List<SysDept> list, Long parentId) {
        return list.stream()
                .filter(d -> Objects.equals(d.getParentId(), parentId))
                .peek(d -> d.setChildren(buildTree(list, d.getId())))
                .collect(Collectors.toList());
    }
}
