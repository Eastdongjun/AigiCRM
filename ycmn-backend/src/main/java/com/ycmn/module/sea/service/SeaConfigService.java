package com.ycmn.module.sea.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.sea.entity.BizSeaConfig;
import com.ycmn.module.sea.mapper.BizSeaConfigMapper;
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
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    /** 全部公海池(供管理页面) */
    public List<BizSeaConfig> list() {
        List<BizSeaConfig> list = configMapper.selectList(
                new LambdaQueryWrapper<BizSeaConfig>().orderByAsc(BizSeaConfig::getSortOrder));
        for (BizSeaConfig config : list) {
            fillNames(config);
        }
        return list;
    }

    /** 当前用户可见的公海池(供公海池页面选择器/列表过滤) */
    public List<BizSeaConfig> listVisible() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        return list().stream().filter(c -> isVisibleTo(c, user)).collect(Collectors.toList());
    }

    public void create(BizSeaConfig config) {
        config.setRegionIds(deriveRegionIds(config.getVisibleDeptIds(), config.getVisibleUserIds()));
        checkOfficeOverlap(config);
        configMapper.insert(config);
    }

    public void update(BizSeaConfig config) {
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

    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    /** 公海池是否对该用户可见: all数据范围全可见; 本人在可见人员中; 本人部门在可见部门中 */
    public boolean isVisibleTo(BizSeaConfig config, SecurityUser user) {
        if (user == null) return false;
        if ("all".equals(user.getDataScope())) return true;
        if (csvContains(config.getVisibleUserIds(), user.getUserId())) return true;
        if (csvContains(config.getVisibleDeptIds(), user.getDeptId())) return true;
        return false;
    }

    /** 由可见部门/人员派生所属办事处(dept_type=3)集合,逗号分隔,用于客户释放路由 */
    private String deriveRegionIds(String deptIdsCsv, String userIdsCsv) {
        Map<Long, SysDept> deptMap = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        LinkedHashSet<Long> offices = new LinkedHashSet<>();
        // 部门 → 向上找最近 dept_type=3 办事处
        for (Long deptId : parseIds(deptIdsCsv)) {
            SysDept cur = deptMap.get(deptId);
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
     * 组织机构树: 部门(type=4)与人员可选, 办事处/大区/总部(type<=3)灰显禁选仅作层级.
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
                boolean selectable = d.getDeptType() != null && d.getDeptType() == 4;
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
