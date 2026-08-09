package com.ycmn.module.sea.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.sea.entity.BizSeaConfig;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaConfigMapper;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeaConfigService {

    private final BizSeaConfigMapper configMapper;
    private final BizSeaPoolMapper seaPoolMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    /** 全部公海池(供管理页面,含禁用池——禁用池也需可查看/重新启用) */
    public List<BizSeaConfig> list() {
        List<BizSeaConfig> list = configMapper.selectList(
                new LambdaQueryWrapper<BizSeaConfig>().orderByAsc(BizSeaConfig::getSortOrder));
        for (BizSeaConfig config : list) {
            fillNames(config);
        }
        return list;
    }

    /**
     * 当前用户可见且启用的公海池(供公海池页面选择器/列表过滤/统计)。
     * 禁用池(status=0)不出现在业务页面——与 matchSeaConfig 释放路由口径一致,
     * 且可捞取/删除可见池记录时才不会被禁用池的存量客户误导。
     */
    public List<BizSeaConfig> listVisible() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        return list().stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .filter(c -> isVisibleTo(c, user))
                .collect(Collectors.toList());
    }

    public void create(BizSeaConfig config) {
        // 【修复 2026-08-09 B2】新池默认启用(status=1),与 DB 默认值一致;前端可显式传 0 建禁用池
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        config.setRegionIds(deriveRegionIds(config.getVisibleDeptIds(), config.getVisibleUserIds()));
        checkOfficeOverlap(config);
        configMapper.insert(config);
    }

    public void update(BizSeaConfig config) {
        // 【修复 2026-08-09 B2】status 为 null 时 updateById 按 MP 默认 NOT_NULL 策略跳过该列(保留原值),
        // 只有前端显式传 0/1 时才变更启用/禁用状态——语义正确,无需特殊分支。
        config.setRegionIds(deriveRegionIds(config.getVisibleDeptIds(), config.getVisibleUserIds()));
        checkOfficeOverlap(config);
        configMapper.updateById(config);
    }

    /**
     * 方案B: 禁止办事处重叠 —— 一个办事处(dept_type=3)只能属于一个公海池。
     * 保存时校验本池派生出的办事处是否已被其它池占用,命中则拒绝并指出冲突的办事处/池。
     * 消除释放路由 matchSeaConfig 的"多池命中、取第一个"歧义(见客户释放路由逻辑)。
     */
    private void checkOfficeOverlap(BizSeaConfig config) {
        List<Long> myOffices = parseIds(config.getRegionIds());
        if (myOffices.isEmpty()) return;
        List<BizSeaConfig> others = configMapper.selectList(
                new LambdaQueryWrapper<BizSeaConfig>()
                        .ne(config.getId() != null, BizSeaConfig::getId, config.getId()));
        for (BizSeaConfig other : others) {
            List<Long> otherOffices = parseIds(other.getRegionIds());
            for (Long officeId : myOffices) {
                if (otherOffices.contains(officeId)) {
                    SysDept dept = deptMapper.selectById(officeId);
                    String officeName = dept != null ? dept.getDeptName() : String.valueOf(officeId);
                    throw new BusinessException("办事处「" + officeName + "」已属于公海池「"
                            + other.getPoolName() + "」,同一办事处不能分配到多个公海池");
                }
            }
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        // 【修复 2026-08-09 B9】删除前检查池内是否仍有客户记录,避免客户关联已删池(孤儿)。
        // 校验与删除同一事务,消除 TOCTOU 竞态(复核 Low: 窗口期定时/手动释放可能插入新记录)。
        Long poolCount = seaPoolMapper.selectCount(
                new LambdaQueryWrapper<BizSeaPool>().eq(BizSeaPool::getPoolId, id));
        if (poolCount != null && poolCount > 0) {
            throw new BusinessException("该公海池下仍有 " + poolCount + " 条客户记录，无法删除");
        }
        configMapper.deleteById(id);
    }

    /** 公海池是否对该用户可见: all数据范围全可见; 本人在可见人员中; 本人部门(或上级链含大区)在可见部门中 */
    public boolean isVisibleTo(BizSeaConfig config, SecurityUser user) {
        if (user == null) return false;
        if ("all".equals(user.getDataScope())) return true;
        if (csvContains(config.getVisibleUserIds(), user.getUserId())) return true;
        if (user.getDeptId() != null && csvContains(config.getVisibleDeptIds(), user.getDeptId())) return true;
        // 大区可见性: 用户部门向上追溯, 命中可见部门(如大区 id)即可见
        if (user.getDeptId() != null && StringUtils.hasText(config.getVisibleDeptIds())) {
            Set<Long> visible = parseIds(config.getVisibleDeptIds()).stream().collect(Collectors.toSet());
            Map<Long, SysDept> deptMap = deptMapper.selectList(null).stream()
                    .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
            SysDept cur = deptMap.get(user.getDeptId());
            int guard = 0;
            while (cur != null && guard++ < 20) {
                if (visible.contains(cur.getId())) return true;
                cur = cur.getParentId() != null ? deptMap.get(cur.getParentId()) : null;
            }
        }
        return false;
    }

    /** 由可见部门/人员派生所属办事处(dept_type=3)集合,逗号分隔,用于客户释放路由 */
    private String deriveRegionIds(String deptIdsCsv, String userIdsCsv) {
        Map<Long, SysDept> deptMap = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Map<Long, List<SysDept>> childrenMap = new HashMap<>();
        for (SysDept d : deptMap.values()) {
            childrenMap.computeIfAbsent(d.getParentId() == null ? 0L : d.getParentId(), k -> new ArrayList<>()).add(d);
        }
        LinkedHashSet<Long> offices = new LinkedHashSet<>();
        // 部门 → 大区(type=2)向下收集其子树所有办事处(3); 其他部门向上找最近 type=3 办事处
        for (Long deptId : parseIds(deptIdsCsv)) {
            SysDept dept = deptMap.get(deptId);
            if (dept == null) continue;
            if (dept.getDeptType() != null && dept.getDeptType() == 2) {
                collectOfficesUnder(dept.getId(), childrenMap, offices);
                continue;
            }
            SysDept cur = dept;
            int guard = 0;
            while (cur != null && (cur.getDeptType() == null || cur.getDeptType() != 3) && guard++ < 20) {
                cur = cur.getParentId() != null ? deptMap.get(cur.getParentId()) : null;
            }
            if (cur != null && cur.getDeptType() != null && cur.getDeptType() == 3) {
                offices.add(cur.getId());
            }
        }
        // 人员 → office_id
        for (Long userId : parseIds(userIdsCsv)) {
            SysUser u = userMapper.selectById(userId);
            if (u != null && u.getOfficeId() != null) offices.add(u.getOfficeId());
        }
        return offices.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 收集某部门(如大区)子树中所有办事处(dept_type=3) */
    private void collectOfficesUnder(Long deptId, Map<Long, List<SysDept>> childrenMap, Set<Long> offices) {
        for (SysDept child : childrenMap.getOrDefault(deptId, Collections.emptyList())) {
            if (child.getDeptType() != null && child.getDeptType() == 3) {
                offices.add(child.getId());
            } else {
                collectOfficesUnder(child.getId(), childrenMap, offices);
            }
        }
    }

    private void fillNames(BizSeaConfig config) {
        if (StringUtils.hasText(config.getRegionIds())) {
            config.setRegionNames(parseIds(config.getRegionIds()).stream()
                    .map(id -> {
                        SysDept dept = deptMapper.selectById(id);
                        return dept != null ? dept.getDeptName() : String.valueOf(id);
                    })
                    .collect(Collectors.joining(", ")));
        }
        List<String> names = new ArrayList<>();
        for (Long id : parseIds(config.getVisibleDeptIds())) {
            SysDept dept = deptMapper.selectById(id);
            if (dept != null) names.add(dept.getDeptName());
        }
        for (Long id : parseIds(config.getVisibleUserIds())) {
            SysUser u = userMapper.selectById(id);
            if (u != null) names.add(u.getRealName());
        }
        config.setVisibleNames(String.join(", ", names));
    }

    /**
     * 组织机构树: 大区/办事处/部门(type=2/3/4)与人员均可选, 总部(type=1)灰显禁选仅作层级.
     * 节点: { id:"d_<id>"/"u_<id>", label, disabled, children }
     */
    public List<Map<String, Object>> buildOrgTree() {
        List<SysDept> depts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder));
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1).orderByAsc(SysUser::getId));
        Map<Long, List<SysUser>> usersByDept = new HashMap<>();
        for (SysUser u : users) {
            usersByDept.computeIfAbsent(u.getDeptId(), k -> new ArrayList<>()).add(u);
        }
        List<Map<String, Object>> tree = buildDeptNodes(depts, 0L, usersByDept);
        // 无有效部门归属的人员归入"未分配"灰显分组,避免漏选
        Set<Long> deptIdSet = depts.stream().map(SysDept::getId).collect(Collectors.toSet());
        List<Map<String, Object>> orphans = new ArrayList<>();
        for (SysUser u : users) {
            if (u.getDeptId() == null || !deptIdSet.contains(u.getDeptId())) {
                orphans.add(userNode(u));
            }
        }
        if (!orphans.isEmpty()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("id", "d_0");
            group.put("label", "未分配部门");
            group.put("disabled", true);
            group.put("children", orphans);
            tree.add(group);
        }
        return tree;
    }

    private List<Map<String, Object>> buildDeptNodes(List<SysDept> all, Long parentId, Map<Long, List<SysUser>> usersByDept) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SysDept d : all) {
            if (Objects.equals(d.getParentId(), parentId)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", "d_" + d.getId());
                node.put("label", d.getDeptName());
                boolean selectable = d.getDeptType() != null && d.getDeptType() != 1;
                node.put("disabled", !selectable);
                List<Map<String, Object>> children = buildDeptNodes(all, d.getId(), usersByDept);
                List<SysUser> us = usersByDept.get(d.getId());
                if (us != null) {
                    for (SysUser u : us) children.add(userNode(u));
                }
                if (!children.isEmpty()) node.put("children", children);
                nodes.add(node);
            }
        }
        return nodes;
    }

    private Map<String, Object> userNode(SysUser u) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", "u_" + u.getId());
        n.put("label", u.getRealName());
        n.put("disabled", false);
        return n;
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
