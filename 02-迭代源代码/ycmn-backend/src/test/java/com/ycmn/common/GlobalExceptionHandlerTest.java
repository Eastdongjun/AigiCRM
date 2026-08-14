package com.ycmn.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 【2026-08-14 错误码语义化】BusinessException code → HTTP 状态映射测试。
 * 403→403、401→401、404→404、429→429、其余默认 400；body 中 code 与 message 原样透传。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessException_403_mapsToForbidden() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(403, "没有访问权限"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals(403, resp.getBody().getCode());
        assertEquals("没有访问权限", resp.getBody().getMessage());
    }

    @Test
    void businessException_401_mapsToUnauthorized() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(401, "未认证"));
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals(401, resp.getBody().getCode());
    }

    @Test
    void businessException_404_mapsToNotFound() {
        ResponseEntity<Result<Void>> resp =
                handler.handleBusinessException(new BusinessException(404, "客户不存在或无权访问"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals(404, resp.getBody().getCode());
        assertEquals("客户不存在或无权访问", resp.getBody().getMessage());
    }

    @Test
    void businessException_429_mapsToTooManyRequests() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(429, "请求过于频繁"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode());
        assertEquals(429, resp.getBody().getCode());
    }

    @Test
    void businessException_defaultConstructor_mapsToBadRequest() {
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException("业务失败"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().getCode());
    }

    @Test
    void businessException_unknownCode_mapsToBadRequest() {
        // 未在语义化映射表内的 code(如 500) → HTTP 默认 400, body code 原样透传(与改前行为一致)
        ResponseEntity<Result<Void>> resp = handler.handleBusinessException(new BusinessException(500, "内部错误"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(500, resp.getBody().getCode());
    }
}
