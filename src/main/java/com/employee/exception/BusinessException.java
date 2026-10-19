package com.employee.exception;

/**
 * 业务异常：由 Service 层主动抛出，GlobalExceptionHandler 统一转成 Result
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
