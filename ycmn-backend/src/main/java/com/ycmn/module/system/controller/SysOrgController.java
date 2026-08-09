package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.service.OrgAdminService;
import com.ycmn.module.system.service.SysDeptService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /api/org 兼容层（收口 sys_org/sys_dept 后保留前端不变）。
 * 内部统一走 SysDeptService + OrgAdminService；对外输出 orgName/orgCode/orgType(String) 旧结构。
 * 接口路径与权限码保持原样，行为与旧 SysOrgService 完全一致（树无数据范围过滤、删除为软删、增改删带 canManageOrg 守卫）。
 */
@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysDeptService deptService;
    private final OrgAdminService orgAdminService;

    /** 获取组织树（全量，无数据范围过滤，语义=旧 SysOrgService.getOrgTree） */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> getOrgTree() {
        return Result.ok(deptService.treeAll().stream().map(this::toOrgNode).collect(Collectors.toList()));
    }

    /** 创建组织 */
    @PreAuthorize("hasAuthority('system:dept:add')")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long parentId = asLong(body.get("parentId"));
        // 【安全 2026-08-08】仅在调用者管辖范围内创建（父组织必须可管理；根组织仅超管）
        if (!orgAdminService.canManageOrg(parentId != null ? parentId : 0L)) {
            throw new com.ycmn.common.BusinessException("无权在该组织下创建组织");
        }
        SysDept dept = toDept(body);
        deptService.create(dept);
        return Result.ok(toOrgNode(dept));
    }

    /** 更新组织 */
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!orgAdminService.canManageOrg(id)) {
            throw new com.ycmn.common.BusinessException("无权修改该组织");
        }
        SysDept dept = toDept(body);
        dept.setId(id);
        deptService.update(dept);
        return Result.ok();
    }

    /** 删除组织（软删：置 status=0，语义=旧 SysOrgService.delete） */
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!orgAdminService.canManageOrg(id)) {
            throw new com.ycmn.common.BusinessException("无权删除该组织");
        }
        deptService.softDelete(id);
        return Result.ok();
    }

    /** 获取组织管理员 */
    @GetMapping("/{orgId}/admins")
    @PreAuthorize("hasAuthority('system:dept:view')")
    public Result<List<Map<String, Object>>> getAdmins(@PathVariable Long orgId) {
        return Result.ok(orgAdminService.getOrgAdmins(orgId));
    }

    /** 添加组织管理员 */
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @PostMapping("/{orgId}/admins")
    public Result<Void> addAdmin(@PathVariable Long orgId, @RequestParam Long userId) {
        orgAdminService.addOrgAdmin(orgId, userId);
        return Result.ok();
    }

    /** 移除组织管理员 */
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @DeleteMapping("/{orgId}/admins/{id}")
    public Result<Void> removeAdmin(@PathVariable Long orgId, @PathVariable Long id) {
        orgAdminService.removeOrgAdmin(id);
        return Result.ok();
    }

    /** 获取当前用户管理范围 */
    @GetMapping("/my-scope")
    public Result<Map<String, Object>> getMyScope() {
        Map<String, Object> scope = new HashMap<>();
        scope.put("orgId", SecurityUtils.getCurrentOrgId());
        scope.put("dataScope", SecurityUtils.getCurrentDataScope());
        scope.put("managedOrgIds", SecurityUtils.getCurrentManagedOrgIds());
        return Result.ok(scope);
    }

    // ===== 映射辅助 =====

    /** SysDept → 旧 org 兼容结构（orgType 必须 String，org.vue el-select 严格相等） */
    private Map<String, Object> toOrgNode(SysDept d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("parentId", d.getParentId());
        m.put("groupId", d.getGroupId());
        m.put("legalEntityId", d.getLegalEntityId());
        m.put("orgName", d.getDeptName());
        m.put("orgCode", d.getDeptCode());
        m.put("orgType", d.getDeptType() != null ? String.valueOf(d.getDeptType()) : null);
        m.put("leaderId", d.getLeaderId());
        m.put("sortOrder", d.getSortOrder());
        m.put("status", d.getStatus());
        m.put("createdAt", d.getCreatedAt());
        m.put("updatedAt", d.getUpdatedAt());
        m.put("children", d.getChildren() == null ? null
                : d.getChildren().stream().map(this::toOrgNode).collect(Collectors.toList()));
        return m;
    }

    /** 旧 org 请求体 → SysDept */
    private SysDept toDept(Map<String, Object> body) {
        SysDept d = new SysDept();
        d.setParentId(asLong(body.get("parentId")));
        d.setDeptName(asString(body.get("orgName")));
        d.setDeptCode(asString(body.get("orgCode")));
        d.setDeptType(parseType(body.get("orgType")));
        d.setGroupId(asLong(body.get("groupId")));
        d.setLegalEntityId(asLong(body.get("legalEntityId")));
        d.setLeaderId(asLong(body.get("leaderId")));
        d.setSortOrder(asInt(body.get("sortOrder")));
        d.setStatus(asInt(body.get("status")));
        return d;
    }

    /** String "3" 或 Number 3 → Integer 3；null/空 → null */
    private Integer parseType(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Long.parseLong(s);
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Integer.parseInt(s);
    }
}
