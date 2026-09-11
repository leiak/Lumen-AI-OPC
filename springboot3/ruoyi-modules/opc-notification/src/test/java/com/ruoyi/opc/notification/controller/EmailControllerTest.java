package com.ruoyi.opc.notification.controller;

import com.ruoyi.opc.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EmailController 切片测试 - 使用 MockMvc standalone 模式,
 * 避开 @WebMvcTest 加载 OpcNotificationApplication 完整上下文
 * (其 @ComponentScan 会把 SmtpEmailProvider/InboxServiceImpl 等也拉进来).
 */
class EmailControllerTest {

    private MockMvc mvc;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        EmailController controller = new EmailController(emailService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void send_validRequest_returns200() throws Exception {
        mvc.perform(post("/opc/notification/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"to":"a@b.com","subject":"hi","body":"<p>x</p>"}
                    """))
            .andExpect(status().isOk());

        verify(emailService).send(eq("a@b.com"), eq("hi"), eq("<p>x</p>"));
    }

    @Test
    void send_invalidEmail_returns400() throws Exception {
        mvc.perform(post("/opc/notification/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"to":"not-an-email","subject":"hi","body":"x"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void send_missingSubject_returns400() throws Exception {
        mvc.perform(post("/opc/notification/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"to":"a@b.com","body":"x"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
