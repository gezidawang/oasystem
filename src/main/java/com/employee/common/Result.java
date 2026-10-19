package com.employee.common;

import java.io.Serializable;

/**
 * 统一响应结果封装
 * 前端 axios 拦截器约定：{ success, code, message, data }
 */
public class Result<T> implements Serializable {

    private boolean success;
    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(boolean success, int code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, 200, "操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(true, 200, message, data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(false, 500, message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(false, code, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
