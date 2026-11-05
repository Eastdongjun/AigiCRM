package com.aigicrm.module.system.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.module.sea.entity.BizSeaConfig;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【统一权限配置中心 2026-11-03 用户裁决"门面统一"】生效预览(只读)。
 *
 * 【需求】管理员配完权限立刻看到"某个用户真实拥有什么", 不用猜、不用拿真人账号试
 * 【方法】对目标 userId 用 UserDetailsServiceImpl 重建完整 SecurityUser(与每请求鉴权同链路),
 *         再喂给各引擎现成的解析方法(OrgScopeResolver/SeaConfigService/IncentiveService)
 * 【业务规则】只读不落库; 管理员目标用户全量可见; 数字=范围口径如实呈现
 *             (可见客户数不含运行时规则引擎条件, 预览只表达范围维度)
 * 【注意】不做任何写操作; 引擎全部复用, 与真实请求判定零漂移
 */
@Service
@RequiredArgsConstructor
public class PermCenterService {

    private final UserDetailsServiceImpl userDetailsService;
    private final OrgScopeResolver scopeResolver;
    private final RolePermScopeService rolePermScopeService;
    private final SeaConfigService seaConfigService;
    private final IncentiveService incentiveService;
    private final CustomerService customerService;

    /** 预览的范围解析目标(与前端权限矩阵 PERMISSION_SCOPE_TARGETS 映射同源; 2026-11-03 对账=摘死配置+补漏配) */
    private static final String[][] SCOPE_TARGETS = {
            {"customer", "view", "客户·查看"},
            {"customer", "edit", "客户·编辑"},
            {"customer", "delete", "客户·删除"},
            {"customer", "finance_edit", "开票回款·编辑"},
            {"reminder", "view", "提醒·查看"},
            {"approval", "view", "审批·查看"},
            {"task", "view", "任务·查看"},
            {"sea", "view", "公海·查看"},
            {"system", "view", "日志·查看(system:log:view, 审计日志共用)"},
            // 【2026-11-03 范围对账】摘 incentive/file 死配置行, 补 statistics/score 真消费行
            {"statistics", "view", "统计分析·查看(工作台/统计可见人员)"},
            {"score", "view", "学分·查看"},
            // 【2026-11-05 用户拍板 B"四项激励独立数据范围"】incentive:stats:view/rank:view 两码
            // 共用 (incentive, stats_view) 独立档位(同一数据族), 与统计分析档位解耦
            {"incentive", "stats_view", "四项激励·统计/排行数据范围(激励统计查看+激励排行查看共用)"},
    };

    public Map<String, Object> preview(Long userId) {
        SecurityUser target = (SecurityUser) userDetailsService.loadUserByUsername(String.valueOf(userId));

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId", target.getUserId());
        userInfo.put("username", target.getUsername());
        userInfo.put("realName", target.getRealName());
        userInfo.put("roleCodes", target.getRoleCodes());
        userInfo.put("dataScope", target.getDataScope());
        userInfo.put("admin", target.hasRole("admin"));
        data.put("user", userInfo);
        data.put("permissions", new ArrayList<>(target.getPermissions()));

        // 范围解析表: 每个真实消费范围的权限码 → 生效范围/锚点/可见人数(可见客户数仅客户查看行)
        List<Map<String, Object>> scopes = new ArrayList<>();
        for (String[] t : SCOPE_TARGETS) {
            String module = t[0];
            String action = t[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("module", module);
            row.put("action", action);
            row.put("label", t[2]);
            List<String> effective = rolePermScopeService.effectiveScopes(target, module, action);
            row.put("scope", effective == null || effective.isEmpty() ? "默认" : String.join(",", effective));
            ScopeRequest.AnchorType anchor = rolePermScopeService.anchorOf(target, module, action);
            row.put("anchor", anchor.name());
            ScopeRequest req = ScopeRequest.builder().user(target).module(module).action(action).anchor(anchor).build();
            ScopeDecision d = scopeResolver.resolveScope(req);
            if (d.isAll()) {
                row.put("visibleUserCount", -1); // -1 = 全部(前端显示"全部")
            } else {
                List<Long> uids = d.getVisibleUserIds();
                row.put("visibleUserCount", uids == null ? 0 : uids.size());
                List<Long> deptIds = d.getVisibleDeptIds();
                row.put("visibleDeptCount", deptIds == null ? 0 : deptIds.size());
            }
            if ("customer".equals(module) && "view".equals(action)) {
                row.put("visibleCustomerCount", customerService.countByScopeFor(target));
            }
            scopes.add(row);
        }
        data.put("scopes", scopes);

        // 公海各池判定(引擎方法本就接受任意 SecurityUser)
        List<Map<String, Object>> pools = new ArrayList<>();
        for (BizSeaConfig pool : seaConfigService.list()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("poolId", pool.getId());
            p.put("poolName", pool.getPoolName());
            p.put("status", pool.getStatus());
            p.put("view", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_VIEW, target));
            p.put("edit", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_EDIT, target));
            p.put("delete", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_DELETE, target));
            p.put("claim", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_CLAIM, target));
            pools.add(p);
        }
        data.put("seaPools", pools);

        // 激励专项授权四开关(无行=默认: 分享可见开, 其余关)
        Map<String, Object> grant = new LinkedHashMap<>();
        Map<String, Object> row = incentiveService.grantRow(target.getUserId());
        grant.put("canViewShared", row == null ? Boolean.TRUE : flag(row, "can_view_shared", true));
        grant.put("canViewAll", row == null ? Boolean.FALSE : flag(row, "can_view_all", false));
        grant.put("canEdit", row == null ? Boolean.FALSE : flag(row, "can_edit", false));
        grant.put("canDelete", row == null ? Boolean.FALSE : flag(row, "can_delete", false));
        data.put("incentive", grant);
        return data;
    }

    private boolean flag(Map<String, Object> row, String column, boolean defaultValue) {
        Object v = row.get(column);
        if (v == null) return defaultValue;
        return Integer.valueOf(1).equals(((Number) v).intValue());
    }
}
