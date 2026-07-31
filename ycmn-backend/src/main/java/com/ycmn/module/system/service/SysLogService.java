package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.module.system.entity.SysLog;
import com.ycmn.module.system.mapper.SysLogMapper;
import com.ycmn.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLogMapper logMapper;

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
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
