package com.ruoyi.common.core.exception;

/**
 * 业务异常
 *
 * <p>W7 改动：移除 {@code final} 修饰符，使 {@code OpcException} 可以继承以复用
 * {@link com.ruoyi.common.security.handler.GlobalExceptionHandler#handleServiceException} 的
 * 异常映射逻辑（{@code code} 字段会被 advice 写入 {@code AjaxResult.code}）。
 *
 * @author ruoyi
 */
public class ServiceException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误提示
     */
    private String message;

    /**
     * 错误明细，内部调试错误
     *
     * 和 {@link CommonResult#getDetailMessage()} 一致的设计
     */
    private String detailMessage;

    /**
     * 空构造方法，避免反序列化问题
     */
    public ServiceException()
    {
    }

    public ServiceException(String message)
    {
        this.message = message;
    }

    public ServiceException(String message, Integer code)
    {
        this.message = message;
        this.code = code;
    }

    public String getDetailMessage()
    {
        return detailMessage;
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    public Integer getCode()
    {
        return code;
    }

    public ServiceException setMessage(String message)
    {
        this.message = message;
        return this;
    }

    public ServiceException setDetailMessage(String detailMessage)
    {
        this.detailMessage = detailMessage;
        return this;
    }
}