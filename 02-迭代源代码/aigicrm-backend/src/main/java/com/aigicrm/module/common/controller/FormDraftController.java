package com.aigicrm.module.common.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.common.scope.GroupScopeUtil;
import com.aigicrm.module.common.service.FormDraftService;
import java.util.LinkedHashMap;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 【议题四裁决 2026-09-19】表单草稿 API。
 * 隔离:按 账号+当前集团+草稿类型(登录即可用,仅本人草稿)。
 * 冲突:PUT 带 version;409 时返回服务器当前草稿供"覆盖/使用服务器版本"选择。
 */
@RestController
@RequestMapping("/api/draft")
@RequiredArgsConstructor
public class FormDraftController {

    private final FormDraftService draftService;

    @GetMapping("/{draftType}")
    @PreAuthorize("isAuthenticated()")
    public Result<java.util.Map<String, Object>> get(@PathVariable String draftType) {
        Long groupId = GroupScopeUtil.currentGroupOrNull();
        if (groupId == null) return Result.ok(null);
        return Result.ok(FormDraftService.toRow(draftService.getDraft(draftType, SecurityUtils.getCurrentUserId(), groupId)));
    }

    @PutMapping("/{draftType}")
    @PreAuthorize("isAuthenticated()")
    public Result<Object> save(@PathVariable String draftType, @RequestBody Map<String, Object> body) {
        Long groupId = GroupScopeUtil.currentGroupOrNull();
        if (groupId == null) throw new BusinessException(403, "无有效集团归属");
        String data = body.get("draftData") != null ? body.get("draftData").toString() : null;
        Integer version = body.get("version") instanceof Number n ? n.intValue() : null;
        try {
            return Result.ok(FormDraftService.toRow(draftService.saveDraft(draftType, SecurityUtils.getCurrentUserId(),
                    groupId, data, version)));
        } catch (FormDraftService.ConflictException e) {
            return Result.error(409, "草稿已在其他设备修改");
        }
    }

    /** 冲突后用户选择"使用我的版本"时调用(强制覆盖)。 */
    @PutMapping("/{draftType}/force")
    @PreAuthorize("isAuthenticated()")
    public Result<java.util.Map<String, Object>> forceSave(@PathVariable String draftType, @RequestBody Map<String, Object> body) {
        Long groupId = GroupScopeUtil.currentGroupOrNull();
        if (groupId == null) throw new BusinessException(403, "无有效集团归属");
        String data = body.get("draftData") != null ? body.get("draftData").toString() : null;
        return Result.ok(FormDraftService.toRow(draftService.forceSaveDraft(draftType, SecurityUtils.getCurrentUserId(), groupId, data)));
    }

    @DeleteMapping("/{draftType}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable String draftType) {
        Long groupId = GroupScopeUtil.currentGroupOrNull();
        if (groupId != null) {
            draftService.deleteDraft(draftType, SecurityUtils.getCurrentUserId(), groupId);
        }
        return Result.ok();
    }

}
