package com.ycmn.module.approval.controller;

import com.ycmn.common.Result;
import com.ycmn.module.approval.entity.BizApprovalConfig;
import com.ycmn.module.approval.service.ApprovalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approval/config")
@RequiredArgsConstructor
public class ApprovalConfigController {

    private final ApprovalConfigService configService;

    /** 获取所有审批流程配置 */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<BizApprovalConfig>> list() {
        return Result.ok(configService.list());
    }

    /** 创建审批配置 */
    @PreAuthorize("hasAuthority('system:config:view')")
    @PostMapping
    public Result<BizApprovalConfig> create(@RequestBody BizApprovalConfig config) {
        return Result.ok(configService.create(config));
    }

    /** 更新审批配置 */
    @PreAuthorize("hasAuthority('system:config:view')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BizApprovalConfig config) {
        config.setId(id);
        configService.update(config);
        return Result.ok();
    }

    /** 删除审批配置 */
    @PreAuthorize("hasAuthority('system:config:view')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.ok();
    }
}
