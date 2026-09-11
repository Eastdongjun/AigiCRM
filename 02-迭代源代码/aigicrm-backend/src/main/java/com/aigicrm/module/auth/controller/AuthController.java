package com.aigicrm.module.auth.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.common.util.ClientIpUtils;
import com.aigicrm.module.auth.dto.*;
import com.aigicrm.module.auth.service.AuthService;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.mapper.SysLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysLogMapper logMapper;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // 客户端 IP 只取可信源（防伪造头绕过限流），限流与审计日志同源
        String ip = ClientIpUtils.getClientIp(httpRequest);
        LoginResponse resp = authService.login(request, ip);
        try {
            SysLog log = new SysLog();
            log.setUsername(request.getUsername());
            log.setModule("登录");
            log.setAction("登录");
            String ua = httpRequest.getHeader("User-Agent");
            log.setDetail(parseUserAgent(ua));
            String ipLog = ClientIpUtils.getClientIp(httpRequest);
            log.setIp(ipLog);
            logMapper.insert(log);
        } catch (Exception ignored) {
            // 尽力而为:登录审计日志落库失败不影响登录主流程
            }
        return Result.ok(resp);
    }

    private String parseUserAgent(String ua) {
        if (ua == null) return "Unknown";
        String browser = "Unknown";
        String os = "Unknown";
        if (ua.contains("Chrome") && !ua.contains("Edg")) browser = "Chrome";
        else if (ua.contains("Edg")) browser = "Edge";
        else if (ua.contains("Firefox")) browser = "Firefox";
        else if (ua.contains("Safari") && !ua.contains("Chrome")) browser = "Safari";
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Mac OS")) os = "macOS";
        else if (ua.contains("Linux")) os = "Linux";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iOS")) os = "iOS";
        return browser + " / " + os;
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo() {
        return Result.ok(authService.getUserInfo());
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        // 【安全 2026-08-10 M2】拉黑当前 token(按 jti)——注销即失效, 防被盗 token 复用
        String auth = httpRequest.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            authService.logout(auth.substring(7));
        }
        return Result.ok();
    }

    /** 【批④ 2026-08-10 多集团会话】切换当前集团(校验归属 → 重签 JWT) */
    @PostMapping("/switch-group")
    public Result<LoginResponse> switchGroup(@RequestBody java.util.Map<String, Object> body,
                                             HttpServletRequest httpRequest) {
        Object gid = body.get("groupId");
        Long groupId = gid == null ? null : Long.parseLong(String.valueOf(gid));
        String auth = httpRequest.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        return Result.ok(authService.switchGroup(token, groupId));
    }

    // ── 忘记密码 ──

    @PostMapping("/forgot-password/lookup")
    public Result<ForgotPasswordLookupResponse> forgotPasswordLookup(
            @Valid @RequestBody ForgotPasswordLookupRequest request, HttpServletRequest httpRequest) {
        // 【安全 2026-08-08 匿名枚举防护】IP 60s 限流，只取可信代理后的真实 IP
        if (authService.lookupRateLimited(ClientIpUtils.getClientIp(httpRequest))) {
            throw new BusinessException(429, "操作太频繁，请稍后再试");
        }
        return Result.ok(authService.lookupUser(request.getUsername()));
    }

    @PostMapping("/forgot-password/send-code")
    public Result<Void> forgotPasswordSendCode(
            @Valid @RequestBody ForgotPasswordSendCodeRequest request, HttpServletRequest httpRequest) {
        // 【安全 2026-08-10 M4】传可信 IP 做发码限流(防枚举驱动短信轰炸)
        authService.sendResetCode(request, ClientIpUtils.getClientIp(httpRequest));
        return Result.ok();
    }

    @PostMapping("/forgot-password/reset")
    public Result<Void> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        authService.resetPassword(request);
        return Result.ok();
    }
}
