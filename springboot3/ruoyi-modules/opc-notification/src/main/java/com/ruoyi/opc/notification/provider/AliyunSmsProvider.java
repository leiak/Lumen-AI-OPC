package com.ruoyi.opc.notification.provider;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 阿里云短信发送实现
 */
@Slf4j
public class AliyunSmsProvider implements SmsProvider {

    private final Client client;
    private final String signName;
    private final ObjectMapper objectMapper;

    public AliyunSmsProvider(Client client, String signName, ObjectMapper objectMapper) {
        this.client = client;
        this.signName = signName;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> vars) {
        try {
            String varsJson = objectMapper.writeValueAsString(vars != null ? vars : Map.of());
            SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam(varsJson);

            SendSmsResponse response = client.sendSmsWithOptions(request, new RuntimeOptions());
            String respCode = response.getBody() != null ? response.getBody().getCode() : null;
            String respMsg = response.getBody() != null ? response.getBody().getMessage() : null;
            if (!"OK".equals(respCode)) {
                log.warn("[sms] aliyun returned code={} msg={}", respCode, respMsg);
                throw new SmsSendException("Aliyun SMS failed: code=" + respCode
                    + " msg=" + respMsg);
            }
            log.info("[sms] sent to={} template={}", phone, templateCode);
        } catch (SmsSendException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new SmsSendException("Failed to serialize SMS vars: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new SmsSendException("Aliyun SMS exception: " + e.getMessage(), e);
        }
    }
}
