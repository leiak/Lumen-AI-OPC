package com.ruoyi.opc.hr.dto;

import lombok.Data;

/**
 * JD 生成 (LLM) 请求体 —— 3b 回归护栏
 *
 * <p>这个端点以前收的是 {@code Map<String,String>}。租户守卫
 * ({@code CompanyIdBodyAdvice}) 靠反射在 body 类型上找 companyId <b>字段</b>,
 * {@code Map} 没有字段可找 → 守卫判定为「不适用」→ 整条 body 路线失效,
 * 客户端在 body 里填的 companyId 原样进业务层, 而端点还拿 {@code 1L} 兜底。
 * 改回 {@code Map} 就是把这个洞重新打开。</p>
 *
 * <p>换成声明式 DTO 之后, 守卫能认到 companyId 字段并按登录态改写它;
 * 剩下的字段名与前端约定保持不变 (title / category / description)。</p>
 *
 * @author OAC
 */
@Data
public class OpcHrJobLlmRequest
{
    /**
     * 租户作用域 —— 服务端按登录态改写, 客户端给什么都不算数。
     *
     * <p>改写后仍为 null 只可能是「登录了但没加入任何公司」——
     * 那时没有作用域可用, 业务层应当认 403, 不能拿默认值兜底。</p>
     */
    private Long companyId;

    /** 岗位标题。 */
    private String title;

    /** 岗位类别 (可空)。 */
    private String category;

    /** 业务描述。 */
    private String description;
}
