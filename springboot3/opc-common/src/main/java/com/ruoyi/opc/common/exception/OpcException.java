package com.ruoyi.opc.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * OPC 业务异常
 *
 * @author OAC
 */
@Getter
public class OpcException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String errorLevel;

    public OpcException(String message) {
        super(message);
        this.code = 500;
        this.errorLevel = "ERROR";
    }

    public OpcException(int code, String message) {
        super(message);
        this.code = code;
        this.errorLevel = "ERROR";
    }

    public OpcException(int code, String message, String errorLevel) {
        super(message);
        this.code = code;
        this.errorLevel = errorLevel;
    }

    public OpcException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.errorLevel = "ERROR";
    }

}
