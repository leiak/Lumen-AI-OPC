package com.ruoyi.opc.common.exception;

import com.ruoyi.common.core.exception.ServiceException;
import lombok.Getter;

import java.io.Serial;

/**
 * OPC 业务异常
 *
 * <p>W7 改动：继承 {@link ServiceException} 而非 {@link RuntimeException}，
 * 使 {@code GlobalExceptionHandler.handleServiceException} 能识别 {@link #getCode()}
 * 字段并写入 {@code AjaxResult.code}。此前 {@code extends RuntimeException} 导致
 * 走到 {@code handleRuntimeException}，自定义 {@code code} 字段被忽略，
 * 响应永远是 {@code code=500}（详见 W6 报告 + OPC-W7-VERIFICATION-...md）。
 *
 * <p>继承链：
 * <pre>
 *   OpcException
 *     └─ ServiceException
 *          └─ RuntimeException
 * </pre>
 *
 * <p>关键实现细节：
 * <ul>
 *   <li>{@link ServiceException#getMessage()} 被重写为返回其 {@code message} 字段 —
 *       {@code super(message)} 会调用 {@code ServiceException(String)} 构造函数正确填充该字段，
 *       子类 OpcException 无需额外赋值</li>
 *   <li>{@link ServiceException#getCode()} 返回 {@link Integer} —
 *       {@link OpcException#getCode()} 由 Lombok {@code @Getter} 生成返回 {@code int}（自动装箱为 Integer），
 *       子类覆盖父类，签名兼容</li>
 *   <li>{@link ServiceException} 的 4-arg {@code (String, Throwable)} 构造器不存在，
 *       故移除原 {@link OpcException#OpcException(String, Throwable)}（生产代码无引用）</li>
 * </ul>
 *
 * @author OAC
 */
@Getter
public class OpcException extends ServiceException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码，会被 {@code GlobalExceptionHandler.handleServiceException} 写入响应 {@code AjaxResult.code}
     */
    private final int code;

    /**
     * 错误级别（{@code "ERROR"} / {@code "SECURITY"} / {@code "WARN"} 等），仅 OPC 内部日志使用
     */
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
}