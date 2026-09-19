package com.aigicrm.module.incentive.controller;

import com.aigicrm.common.Result;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 激励授权面板（2026-08-16 授权表化）：
 * 由"载体角色绑定"（2026-08-14 M1）改为"用户级授权表 biz_incentive_grant"——
 * "激励-分享可见/激励-全量查看"不是角色概念，激励专项授权（看分享/看全部/编辑/删除）
 * 按人勾选存储，判定由 IncentiveService 查表完成（不走全局权限码/缓存）。
 * 权限码 incentive:view-shared/view-all/manage 已废弃（菜单行保留仅作字典存档）。
 */
@RestController
@RequestMapping("/api/incentive/permission")
@RequiredArgsConstructor
public class IncentivePermissionController {

    private final JdbcTemplate jdbc;
    private final IncentiveService incentiveService;

    /**
     * 激励授权面板列表：所有存在专项授权记录的用户及其四个开关
     *
     * 【需求】系统设置→激励专项权限 表格数据源
     * 【方法】biz_incentive_grant JOIN sys_user，按 user_id 排序
     * 【业务规则】仅 system:config:view 权限可看；无授权记录的用户不出现
     * （默认语义由 Service 处理，无需逐人列行）
     * 【参数】无
     * 【返回】[{id=userId, userId, username, realName, canViewShared, canViewAll, canEdit, canDelete}]
     * 【注意】只读
     */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT g.user_id AS id, g.user_id AS userId, u.username, u.real_name AS realName, " +
            "g.can_view_shared AS canViewShared, g.can_view_all AS canViewAll, " +
            "g.can_edit AS canEdit, g.can_delete AS canDelete " +
            "FROM biz_incentive_grant g " +
            "JOIN sys_user u ON u.id = g.user_id AND u.deleted = 0 " +
            "ORDER BY g.user_id");
        return Result.ok(list);
    }

    /**
     * 批量授予激励专项权限（按人勾选四个开关）
     *
     * 【需求】激励授权面板"添加授权"：谁能看分享/看全部/编辑/删除他人记录
     * 【方法】对每个 userId upsert biz_incentive_grant 行（幂等）
     * 【业务规则】仅系统管理员可操作（看全部/编辑/删除=敏感能力，防 system:config:edit
     *             落到非超管角色后形成局部提权）；canViewShared 未传默认 1（开）
     * 【参数】body: userIds[] 或 userId；canViewShared/canViewAll/canEdit/canDelete (0/1)
     * 【返回】无
     * 【注意】表驱动判定实时生效，无需清权限缓存
     */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, Object> body) {
        // 【安全 2026-08-08】授予激励权限=授予"看全公司激励记录"能力，仅超管可操作
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可授予激励权限");

        int canViewShared = toInt(body.get("canViewShared"), 1);
        int canViewAll = toInt(body.get("canViewAll"), 0);
        int canEdit = toInt(body.get("canEdit"), 0);
        int canDelete = toInt(body.get("canDelete"), 0);

        // 批量授权：userIds 数组优先；兼容单用户 userId（向后兼容）
        java.util.List<Long> userIds = new java.util.ArrayList<>();
        Object uids = body.get("userIds");
        if (uids instanceof java.util.List && !((java.util.List<?>) uids).isEmpty()) {
            for (Object o : (java.util.List<?>) uids) {
                if (o != null) userIds.add(Long.valueOf(o.toString()));
            }
        } else if (body.get("userId") != null) {
            userIds.add(Long.valueOf(body.get("userId").toString()));
        }
        if (userIds.isEmpty()) throw new BusinessException("请选择要授权的人员");

        Long operator = SecurityUtils.getCurrentUserId();
        for (Long userId : userIds) {
            jdbc.update("INSERT INTO biz_incentive_grant " +
                    "(user_id, can_view_shared, can_view_all, can_edit, can_delete, updated_by) " +
                    "VALUES (?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE can_view_shared=VALUES(can_view_shared), " +
                    "can_view_all=VALUES(can_view_all), can_edit=VALUES(can_edit), " +
                    "can_delete=VALUES(can_delete), updated_by=VALUES(updated_by)",
                    userId, canViewShared, canViewAll, canEdit, canDelete, operator);
        }
        return Result.ok();
    }

    private int toInt(Object v, int defaultValue) {
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return defaultValue;
    }

    /**
     * 撤销某人的全部激励专项授权（恢复默认：分享可见=开，其余=关）
     *
     * 【需求】激励授权面板"撤销"
     * 【方法】删除 biz_incentive_grant 行（无行=默认语义由 Service 处理）
     * 【业务规则】仅系统管理员可操作；撤销后该用户只能看本人记录+他人已分享记录
     * 【参数】id 即 userId（与 /list 的 id 契约一致）
     * 【返回】无
     * 【注意】表驱动判定实时生效
     */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable Long id) {
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可撤销激励权限");
        jdbc.update("DELETE FROM biz_incentive_grant WHERE user_id = ?", id);
        return Result.ok();
    }

    /**
     * 当前登录人的激励专项权限自检（前端上传页/统计页判定按钮与数据范围）
     *
     * 【需求】前端知道当前用户 canViewAll/canViewShared/canEdit/canDelete
     * 【方法】复用 IncentiveService 的表驱动判定，与业务过滤口径完全一致
     * 【业务规则】admin 的 canViewAll/canEdit/canDelete 恒 true（isAdmin 兜底）
     * 【参数】无
     * 【返回】{canViewAll, canViewShared, canEdit, canDelete}
     * 【注意】无权限注解（登录即可自检）
     */
    @GetMapping("/check")
    public Result<Map<String, Boolean>> check() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("canViewAll", incentiveService.canViewAll(null));
        result.put("canViewShared", incentiveService.canViewShared(null));
        result.put("canEdit", incentiveService.canEdit());
        result.put("canDelete", incentiveService.canDelete());
        return Result.ok(result);
    }
}
