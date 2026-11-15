package com.aigicrm.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    /** 机器可读错误码(可选): 前端/测试据此分支, 不再靠文案猜测 */
    private String errorCode;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(int code, String message) {
        return error(code, message, null);
    }

    public static <T> Result<T> error(int code, String message, String errorCode) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setErrorCode(errorCode);
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}
