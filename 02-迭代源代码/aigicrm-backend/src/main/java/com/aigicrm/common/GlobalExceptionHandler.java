package com.aigicrm.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLSyntaxErrorException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 【PR-24 首批 2026-09-19】异常支持编号 ====================
    // 总册 PR-24:"异常给用户可记录的脱敏支持编号,内部关联环境、版本、业务对象与操作链"。
    // 对外只回短编号(无堆栈/无内部信息泄露);服务端日志以编号为首键打印完整栈,
    // 支持人员凭编号即可检索。仅用于非预期 500(业务异常文案本身面向用户,不加编号)。

    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    static String supportId() {
        return "S-" + LocalDateTime.now().format(TS) + "-"
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000, 0xFFFFF));
    }

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        int code = e.getCode() > 0 ? e.getCode() : 400;
        return ResponseEntity.status(httpStatusOf(code)).body(Result.error(code, e.getMessage()));
    }

    /** 【2026-08-14 错误码语义化】业务异常 code → HTTP 状态: 403/401/404/429 语义化映射, 其余默认 400 */
    private static HttpStatus httpStatusOf(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    // ==================== 认证授权 ====================

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e) {
        return Result.error(401, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        return Result.error(403, "没有访问权限，请联系管理员授权");
    }

    // ==================== 请求参数 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(400, message != null ? message : "请求参数校验失败");
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return Result.error(400, message != null ? message : "请求参数格式不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(400, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        return Result.error(400, "参数类型不正确：" + e.getName() + " 应为" + (requiredType != null ? requiredType.getSimpleName() : "数字"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.error(400, "请求体格式不正确，请检查JSON格式");
    }

    // ==================== 请求方法/路径 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(405, "不支持的请求方式，请使用 " + e.getSupportedHttpMethods());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.error(404, "请求的地址不存在");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return Result.error(415, "不支持的Content-Type，请使用 application/json");
    }

    // ==================== 文件上传 ====================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.error(400, "文件大小超过限制，最大允许 500MB");
    }

    // ==================== 数据库 ====================

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleDataAccess(DataAccessException e) {
        String sid = supportId();
        log.error("[{}] 数据库异常", sid, e);
        Throwable cause = e.getCause();
        if (cause instanceof SQLSyntaxErrorException) {
            return Result.error(500, "数据查询出错，支持编号 " + sid);
        }
        return Result.error(500, "数据访问异常，支持编号 " + sid);
    }

    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleSQLSyntaxError(SQLSyntaxErrorException e) {
        String sid = supportId();
        log.error("[{}] SQL语法错误", sid, e);
        return Result.error(500, "数据查询出错，支持编号 " + sid);
    }

    // ==================== 兜底 ====================

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        String sid = supportId();
        // 编号置首,运维 grep 编号即得完整栈;响应不含任何内部细节
        log.error("[{}] 系统异常", sid, e);
        return Result.error(500, "系统异常，支持编号 " + sid);
    }
}
