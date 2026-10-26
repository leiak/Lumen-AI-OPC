package com.ruoyi.opc.user.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 切换当前公司（租户）的请求体
 *
 * <p><b>字段为什么叫 {@code targetCompanyId} 而不是 {@code companyId}</b>：
 * 请求体里凡是叫 {@code companyId} 的字段，都会被租户守卫的
 * {@code CompanyIdBodyAdvice} 改写成「当前登录态所在的公司」——
 * 那样这个端点收到的永远是当前公司，切换就永远切不过去。
 * 改用 {@code targetCompanyId} 既绕开了改写，语义也更准：
 * 客户端要的是「目标」，不是「当前」。</p>
 *
 * @author OAC
 */
@Data
public class OpcCompanySwitchRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 要切换到的公司 id */
    private Long targetCompanyId;

}
