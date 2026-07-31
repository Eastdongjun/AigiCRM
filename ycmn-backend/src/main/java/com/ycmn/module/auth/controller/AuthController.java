package com.ycmn.module.auth.controller;

import com.ycmn.common.Result;
import com.ycmn.module.auth.dto.*;
import com.ycmn.module.auth.service.AuthService;
import com.ycmn.module.system.entity.SysLog;
import com.ycmn.module.system.mapper.SysLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysLogMapper logMapper;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = httpRequest.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = httpRequest.getRemoteAddr();
        LoginResponse resp = authService.login(request, ip);
        // 记录登录日志
        try {
            SysLog log = new SysLog();
            log.setUsername(request.getUsername());
            log.setModule("登录");
            log.setAction("登录");
            String ua = httpRequest.getHeader("User-Agent");
            log.setDetail(parseUserAgent(ua));
            String ipLog = httpRequest.getHeader("X-Forwarded-For");
            if (ipLog == null || ipLog.isEmpty()) ipLog = httpRequest.getHeader("X-Real-IP");
            if (ipLog == null || ipLog.isEmpty()) ipLog = httpRequest.getRemoteAddr();
            log.setIp(ipLog);
            logMapper.insert(log);
        } catch (Exception ignored) {}
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
    public Result<Void> logout() {
        return Result.ok();
    }

    // ── 忘记密码 ──

    @PostMapping("/forgot-password/lookup")
    public Result<ForgotPasswordLookupResponse> forgotPasswordLookup(
            @Valid @RequestBody ForgotPasswordLookupRequest request) {
        return Result.ok(authService.lookupUser(request.getUsername()));
    }

    @PostMapping("/forgot-password/send-code")
    public Result<Void> forgotPasswordSendCode(
            @Valid @RequestBody ForgotPasswordSendCodeRequest request) {
        authService.sendResetCode(request);
        return Result.ok();
    }

    @PostMapping("/forgot-password/reset")
    public Result<Void> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        authService.resetPassword(request);
        return Result.ok();
    }
}
