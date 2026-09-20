package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLogMapper logMapper;
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;

    public IPage<SysLog> page(int current, int size, String module, String username,
                              String startDate, String endDate, String keyword) {
        LambdaQueryWrapper<SysLog> wrapper = buildBase(module, startDate, endDate);
        if (username != null && !username.isEmpty()) {
            wrapper.like(SysLog::getUsername, username);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysLog::getUsername, keyword)
                    .or().like(SysLog::getDetail, keyword));
        }
        wrapper.orderByDesc(SysLog::getCreatedAt);
        return logMapper.selectPage(new Page<>(current, size), wrapper);
    }

    /** 按模块统计日志数量（含日期过滤） */
    public java.util.Map<String, Long> countByModule(String startDate, String endDate) {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put("全部", logMapper.selectCount(buildBase(null, startDate, endDate)));
        for (String m : new String[]{"客户", "登录", "文件", "人员", "系统"}) {
            counts.put(m, logMapper.selectCount(buildBase(m, startDate, endDate)));
        }
        return counts;
    }

    private LambdaQueryWrapper<SysLog> buildBase(String module, String startDate, String endDate) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        // 数据范围（与业务模块一致，含子部门递归）：admin 全量；
        // 非管理员 = Resolver 可见用户集合（self 仅自己 / dept 本部门及子部门 / region 大区 / team 本人及下级 / all 全量）
        if (!SecurityUtils.isAdmin()) {
            // 【2026-09-19 接入权限矩阵】(system,view) 显式配置优先，未配置回退角色数据范围
            java.util.List<Long> uids = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "system");
            if (uids == null) {
                // all 范围：本集团全部
            } else if (uids.isEmpty()) {
                w.eq(SysLog::getUserId, Long.MIN_VALUE); // 空集
            } else {
                w.in(SysLog::getUserId, uids);
            }
        }
        if (module != null && !module.isEmpty()) w.eq(SysLog::getModule, module);
        if (startDate != null && !startDate.isEmpty())
            w.ge(SysLog::getCreatedAt, java.time.LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null && !endDate.isEmpty())
            w.lt(SysLog::getCreatedAt, java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        return w;
    }

    public void log(String module, String action, String targetType, Long targetId, String detail, HttpServletRequest request) {
        SysLog log = new SysLog();
        log.setUserId(SecurityUtils.getCurrentUserId());
        log.setUsername(SecurityUtils.getCurrentUsername());
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        if (request != null) {
            log.setIp(getClientIp(request));
        }
        logMapper.insert(log);
    }

    private String getClientIp(HttpServletRequest request) {
        // M2: 统一走可信代理校验，避免直信 X-Forwarded-For 导致审计 IP 可伪造（与登录限流 ClientIpUtils 同源）
        return com.aigicrm.common.util.ClientIpUtils.getClientIp(request);
    }
}
