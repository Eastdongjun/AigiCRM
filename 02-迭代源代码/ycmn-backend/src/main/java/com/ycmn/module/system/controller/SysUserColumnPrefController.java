package com.ycmn.module.system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycmn.common.Result;
import com.ycmn.module.system.service.SysUserColumnPrefService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户表格列显示偏好接口（按人持久化）
 */
@RestController
@RequestMapping("/api/system/user-column-prefs")
@RequiredArgsConstructor
public class SysUserColumnPrefController {

    private final SysUserColumnPrefService columnPrefService;
    // 项目无全局 ObjectMapper bean(Boot 4 未自动装配), 按 ApprovalConfigService 惯例实例级持有
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询当前用户某页面的列偏好
     *
     * 【需求】前端进入表格页面时拉取该用户的列设置
     * 【方法】读 sys_user_column_pref 表 (user_id, page_key) 唯一行
     * 【业务规则】仅返回本人偏好；无记录返回 null（前端用默认全显列）
     * 【参数】pageKey 页面标识（如 incentive-record）
     * 【返回】Result<List<String>> 显示列 key 列表；无记录 data=null
     * 【注意】登录即可用，无额外权限要求
     */
    @GetMapping("/{pageKey}")
    public Result<List<String>> get(@PathVariable String pageKey) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.ok(null);
        }
        String json = columnPrefService.getColumns(userId, pageKey);
        if (json == null) {
            return Result.ok(null);
        }
        try {
            return Result.ok(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            // 偏好数据损坏时按无偏好处理，不让前端白屏
            return Result.ok(null);
        }
    }

    /**
     * 保存当前用户某页面的列偏好
     *
     * 【需求】用户在"列设置"弹窗里点确定时保存
     * 【方法】upsert sys_user_column_pref（存在更新/不存在插入）
     * 【业务规则】body {pageKey, columns:[...]}；columns 只保留前端定义的合法 key（非法值由前端过滤）
     * 【参数】body 含 pageKey 与 columns 数组
     * 【返回】Result<Void>
     * 【注意】columns 大小有限（最多几十个 key），无额外校验；JSON 序列化由 Jackson 完成
     */
    @PutMapping
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        String pageKey = body.get("pageKey") != null ? body.get("pageKey").toString() : "";
        Object cols = body.get("columns");
        if (pageKey.isBlank() || !(cols instanceof List)) {
            return Result.error(400, "pageKey/columns 不能为空");
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(cols);
        } catch (Exception e) {
            return Result.error(400, "columns 格式错误");
        }
        columnPrefService.saveColumns(userId, pageKey, json);
        return Result.ok();
    }
}
