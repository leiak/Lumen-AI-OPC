package com.ruoyi.opc.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮件发送请求
 */
@Data
public class EmailSendRequest {
    @Email(message = "收件人邮箱格式不正确")
    @NotBlank(message = "收件人不能为空")
    private String to;

    @NotBlank(message = "主题不能为空")
    private String subject;

    @NotBlank(message = "正文不能为空")
    private String body;
}
