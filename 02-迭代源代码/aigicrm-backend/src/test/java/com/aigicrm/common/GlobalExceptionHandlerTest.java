package com.aigicrm.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 【2026-08-14 错误码语义化】BusinessException code → HTTP 状态映射测试。
 * 403→403、401→401、404→404、429→429、其余默认 400；body 中 code 与 message 原样透传。
 */
class GlobalExceptionHandlerTest {

    // hint 服务传 null 依赖: hint() 全链路 try-catch 降级 → 恒返回 fallback, 供单测无需 MVC/DB
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new com.aigicrm.security.PermissionHintService(null, null));

    @Test
    void businessException_403_mapsToForbidden() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(403, "没有访问权限"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals(403, body(resp).getCode());
        // 【2026-11-08 权限报错中文化】403 且文案未含"权限中心"→ 原文案保留 + 追加自助指引后缀
        assertTrue(body(resp).getMessage().startsWith("没有访问权限"), "原文案保留在首: " + body(resp).getMessage());
        assertTrue(body(resp).getMessage().contains("【系统设置 → 权限中心】"), "须含权限中心指引: " + body(resp).getMessage());
    }

    // ===== 【2026-11-08 权限报错中文化】403 业务异常追加权限中心指引后缀 =====

    private static final String PERM_SUFFIX =
            " 如需访问此内容，请联系管理员在【系统设置 → 权限中心】开启相应功能或调整数据范围。";

    @Test
    void businessException_403_appendsPermissionCenterSuffix() {
        Result<Void> result = body(handler.handleBusinessException(new BusinessException(403, "无权删除该文件")));
        assertEquals("无权删除该文件" + PERM_SUFFIX, result.getMessage());
    }

    @Test
    void businessException_403_messageAlreadyContainsPermissionCenter_noDoubleSuffix() {
        // 已含"权限中心"的文案（如 PermissionHintService 生成的指引经业务侧透传）不重复追加
        String guided = "您的权限不支持此访问。如需访问此内容，请联系管理员在【系统设置 → 权限中心】检查角色功能权限与数据范围。";
        Result<Void> result = body(handler.handleBusinessException(new BusinessException(403, guided)));
        assertEquals(guided, result.getMessage());
    }

    @Test
    void businessException_non403_noSuffixAppended() {
        // 其余码(400/404/429…)不追加权限指引
        assertEquals("客户不存在或无权访问",
                body(handler.handleBusinessException(new BusinessException(404, "客户不存在或无权访问"))).getMessage());
        assertEquals("业务失败",
                body(handler.handleBusinessException(new BusinessException("业务失败"))).getMessage());
    }

    @Test
    void businessException_401_mapsToUnauthorized() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(401, "未认证"));
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals(401, body(resp).getCode());
    }

    @Test
    void businessException_404_mapsToNotFound() {
        ResponseEntity<Result<Void>> resp =
                handler.handleBusinessException(new BusinessException(404, "客户不存在或无权访问"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals(404, body(resp).getCode());
        assertEquals("客户不存在或无权访问", body(resp).getMessage());
    }

    @Test
    void businessException_429_mapsToTooManyRequests() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(429, "请求过于频繁"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode());
        assertEquals(429, body(resp).getCode());
    }

    @Test
    void businessException_defaultConstructor_mapsToBadRequest() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException("业务失败"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, body(resp).getCode());
    }

    @Test
    void businessException_unknownCode_mapsToBadRequest() {
        // 未在语义化映射表内的 code(如 500) → HTTP 默认 400, body code 原样透传(与改前行为一致)
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(500, "内部错误"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(500, body(resp).getCode());
    }

    /** 空指针安全取 body：handler 返回的 body 不应为空，断言兜底（消除静态分析告警） */
    private static Result<Void> body(ResponseEntity<Result<Void>> resp) {
        Result<Void> b = resp.getBody();
        assertNotNull(b);
        return b;
    }

    // ===== 【PR-24 首批 2026-09-19】异常支持编号:对外脱敏、编号可检索 =====

    private static final java.util.regex.Pattern SID =
            java.util.regex.Pattern.compile("^S-\\d{14}-[0-9a-f]{4,5}$");

    @Test
    void supportId_formatIsShortAndSanitized() {
        String sid = GlobalExceptionHandler.supportId();
        assertTrue(SID.matcher(sid).matches(), "编号形如 S-<时间戳>-<随机hex>,实际: " + sid);
        assertTrue(sid.length() <= 22, "编号须短(用户可手抄),实际长度 " + sid.length());
    }

    @Test
    void supportId_uniqueAcrossCalls() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(GlobalExceptionHandler.supportId());
        }
        // 同秒内仅随机段区分;断言基本唯一(仍能抓住"随机段写死"类缺陷)
        assertTrue(seen.size() >= 198, "编号基本唯一,实际唯一数 " + seen.size());
    }

    @Test
    void fallback500_messageCarriesSupportIdWithoutInternalDetails() {
        Result<Void> result = handler.handleException(
                new RuntimeException("jdbc:mysql://prod-db/internal?password=x"));
        assertNotNull(result);
        assertEquals(500, result.getCode());
        String msg = String.valueOf(result.getMessage());
        assertTrue(msg.contains("支持编号 S-"), "响应须含支持编号: " + msg);
        // 脱敏:异常消息里的内部细节(连接串/口令)不得透出
        assertTrue(!msg.contains("jdbc") && !msg.contains("password"), "响应不得泄露内部细节: " + msg);
        String sid = msg.substring(msg.indexOf("S-"));
        assertTrue(SID.matcher(sid).matches(), "编号可解析: " + sid);
    }

    @Test
    void dataAccess500_messageCarriesSupportId() {
        org.springframework.dao.DataAccessResourceFailureException dae =
                new org.springframework.dao.DataAccessResourceFailureException("connection refused to 10.0.0.9:3306");
        Result<Void> result = handler.handleDataAccess(dae);
        String msg = String.valueOf(result.getMessage());
        assertTrue(msg.contains("支持编号 S-"), "数据访问异常响应须含支持编号");
        assertTrue(!msg.contains("10.0.0.9"), "内部主机地址不得泄露");
    }

    // ===== 【六层验收 2026-11-08】空body 500 修复：multipart 缺陷收口 400 =====

    @Test
    void multipartException_mapsTo400() {
        // 空 JSON body 打到 multipart 端点(/file/upload 等)抛 MultipartException, 修复前落兜底 500
        Result<Void> result = handler.handleMultipart(
                new org.springframework.web.multipart.MultipartException("Current request is not a multipart request"));
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage() != null && result.getMessage().contains("multipart/form-data"),
                "提示须指明 multipart 表单: " + result.getMessage());
    }

    @Test
    void missingServletRequestPart_mapsTo400() {
        // multipart 请求缺 file part, 修复前落兜底 500
        Result<Void> result = handler.handleMissingPart(
                new org.springframework.web.multipart.support.MissingServletRequestPartException("file"));
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage() != null && result.getMessage().contains("file"),
                "提示须指明缺失的 part 名: " + result.getMessage());
    }
}
