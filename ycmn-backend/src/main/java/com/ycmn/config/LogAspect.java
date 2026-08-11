package com.ycmn.config;

import com.ycmn.module.system.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 全局操作日志 AOP 切面
 * 自动拦截所有 Controller 的写操作(POST/PUT/DELETE)并记录到 sys_log
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysLogService logService;

    @AfterReturning(pointcut =
            "execution(* com.ycmn.module..controller..*(..)) && (" +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void logWriteOperation(JoinPoint jp) {
        try {
            HttpServletRequest request = getRequest();
            if (request == null) return;

            String uri = request.getRequestURI();
            String method = request.getMethod();

            // 跳过不需要记录的接口
            if (uri.contains("/auth/login") || uri.contains("/auth/logout")) return;

            String module = resolveModule(uri);
            String action = resolveAction(method, uri);
            String detail = resolveDetail(jp, uri);

            logService.log(module, action, null, null, detail, request);
        } catch (Exception e) {
            log.debug("日志记录失败: {}", e.getMessage());
        }
    }

    /** URL → 模块名 */
    private String resolveModule(String uri) {
        if (uri.contains("/customer")) return "客户";
        if (uri.contains("/file")) return "文件";
        if (uri.contains("/approval")) return "客户";
        if (uri.contains("/sea")) return "客户";
        if (uri.contains("/incentive")) return "文件";
        if (uri.contains("/score")) return "系统";
        if (uri.contains("/system/users") || uri.contains("/system/user")) return "人员";
        if (uri.contains("/system/role")) return "系统";
        if (uri.contains("/system/dept")) return "系统";
        if (uri.contains("/system/config")) return "系统";
        if (uri.contains("/system/dict")) return "系统";
        if (uri.contains("/reminder")) return "系统";
        return "系统";
    }

    /** HTTP method + URL → 动作 */
    private String resolveAction(String method, String uri) {
        if (uri.contains("/approve")) return "审批通过";
        if (uri.contains("/reject")) return "审批驳回";
        if (uri.contains("/release")) return "释放客户";
        if (uri.contains("/upload")) return "上传";
        if (uri.contains("/share")) return "分享";
        if (uri.contains("/reset-password")) return "重置密码";
        if (uri.contains("/change-password")) return "修改密码";
        if (uri.contains("/batch")) return "批量保存";

        return switch (method) {
            case "POST" -> "创建";
            case "PUT" -> "编辑";
            case "DELETE" -> "删除";
            default -> method;
        };
    }

    /** 从方法参数中提取可读的操作详情 */
    private String resolveDetail(JoinPoint jp, String uri) {
        Object[] args = jp.getArgs();
        for (Object arg : args) {
            if (arg == null) continue;
            // 跳过 request/response 对象
            if (arg instanceof HttpServletRequest || arg instanceof jakarta.servlet.http.HttpServletResponse) continue;
            if (arg instanceof org.springframework.web.multipart.MultipartFile mf) {
                return mf.getOriginalFilename();
            }
            // Map 类型：尝试提取常见字段
            if (arg instanceof Map<?, ?> map) {
                for (String key : new String[]{"customerName", "name", "realName", "roleName",
                        "deptName", "dictName", "reason", "type", "comment"}) {
                    Object v = map.get(key);
                    if (v != null && !v.toString().isEmpty()) return v.toString();
                }
            }
            // Entity 类型：反射获取 name 类字段
            try {
                for (String getter : new String[]{"getCustomerName", "getRealName", "getRoleName",
                        "getDeptName", "getFileName", "getDictName", "getCategoryName", "getProduct"}) {
                    var m = arg.getClass().getMethod(getter);
                    Object v = m.invoke(arg);
                    if (v != null && !v.toString().isEmpty()) return v.toString();
                }
            } catch (Exception ignored) {}
        }
        // 从 URL 提取 path variable ID
        String[] parts = uri.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) return "ID:" + parts[i];
        }
        return "";
    }

    private HttpServletRequest getRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
