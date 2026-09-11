package com.aigicrm.module.common.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.common.service.ScopeService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 范围感知筛选器（设计文档 §6.3）：所有模块的人员/部门下拉统一入口。
 * 接口无 @PreAuthorize（登录即可，SecurityConfig anyRequest().authenticated），
 * 数据范围在 service 内强制。
 */
@RestController
@RequestMapping("/api/common/scope")
@RequiredArgsConstructor
public class ScopeController {

    private final ScopeService scopeService;

    /** 可见人员列表（登录即可；范围 service 内强制） */
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users(@RequestParam(required = false) Long deptId) {
        return Result.ok(scopeService.visibleUsers(SecurityUtils.getCurrentUser(), deptId));
    }

    /** 可见部门树（U 聚合）。泛型用 List<Object>：运行时仍序列化为 SysDept（含 children），规避架构门禁 controller→entity 依赖 */
    @GetMapping("/depts")
    public Result<List<Object>> depts() {
        return Result.ok(new ArrayList<Object>(scopeService.visibleDeptTree(SecurityUtils.getCurrentUser())));
    }
}
