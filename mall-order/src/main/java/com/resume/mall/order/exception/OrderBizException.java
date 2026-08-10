package com.resume.mall.order.exception;

public class OrderBizException extends RuntimeException {
    private final int code;
    private final Object data;

    public OrderBizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
