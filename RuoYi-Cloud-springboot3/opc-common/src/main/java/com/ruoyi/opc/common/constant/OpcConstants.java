package com.ruoyi.opc.common.constant;

/**
 * OPC 业务常量
 *
 * @author OAC
 */
public final class OpcConstants {

    private OpcConstants() {}

    /** 系统标识 */
    public static final String SYSTEM_CODE = "OAC";

    /** 用户类型 */
    public static final String USER_TYPE_ENTREPRENEUR = "ENTREPRENEUR";
    public static final String USER_TYPE_SERVICE = "SERVICE";
    public static final String USER_TYPE_OWNER = "OWNER";
    public static final String USER_TYPE_INVESTOR = "INVESTOR";

    /** Agent 分类 */
    public static final String CATEGORY_FINANCE = "FINANCE";
    public static final String CATEGORY_ERP = "ERP";
    public static final String CATEGORY_CRM = "CRM";
    public static final String CATEGORY_HR = "HR";
    public static final String CATEGORY_ECOM = "ECOM";
    public static final String CATEGORY_CONTENT = "CONTENT";
    public static final String CATEGORY_VOICE = "VOICE";
    public static final String CATEGORY_INSIGHT = "INSIGHT";

    /** 雇佣类型 */
    public static final String HIRE_MONTHLY = "MONTHLY";
    public static final String HIRE_QUARTERLY = "QUARTERLY";
    public static final String HIRE_YEARLY = "YEARLY";
    public static final String HIRE_TRIAL = "TRIAL";

    /** 模型标识 */
    public static final String MODEL_DEEPSEEK_V3 = "deepseek-v3";
    public static final String MODEL_GPT_4O_MINI = "gpt-4o-mini";
    public static final String MODEL_GPT_4O = "gpt-4o";
    public static final String MODEL_WENXIN = "wenxin-4.0";
    public static final String MODEL_QWEN = "qwen-max";

    /** Token 单价（元 / 1k tokens） */
    public static final double PRICE_DEEPSEEK_INPUT = 0.001;
    public static final double PRICE_DEEPSEEK_OUTPUT = 0.002;
    public static final double PRICE_GPT_4O_MINI_INPUT = 0.015;
    public static final double PRICE_GPT_4O_MINI_OUTPUT = 0.060;
    public static final double PRICE_GPT_4O_INPUT = 0.250;
    public static final double PRICE_GPT_4O_OUTPUT = 0.750;

    /** Redis Key 前缀 */
    public static final String REDIS_KEY_PREFIX = "opc:";
    public static final String REDIS_SESSION_PREFIX = REDIS_KEY_PREFIX + "session:";
    public static final String REDIS_MEMORY_PREFIX = REDIS_KEY_PREFIX + "memory:";
    public static final String REDIS_RATE_LIMIT_PREFIX = REDIS_KEY_PREFIX + "rate:";

    /** RocketMQ / RabbitMQ Topic */
    public static final String MQ_AGENT_TASK = "opc.agent.task";
    public static final String MQ_AGENT_WORKFLOW = "opc.agent.workflow";

    /** 任务状态 */
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_RUNNING = "RUNNING";
    public static final String TASK_SUCCESS = "SUCCESS";
    public static final String TASK_FAILED = "FAILED";
    public static final String TASK_REVIEW = "REVIEW";

    /** 订单类型 */
    public static final String ORDER_RECHARGE = "RECHARGE";
    public static final String ORDER_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String ORDER_CONSUME = "CONSUME";
    public static final String ORDER_REFUND = "REFUND";

    /** 订单支付状态 */
    public static final String PAY_PENDING = "PENDING";
    public static final String PAY_PAID = "PAID";
    public static final String PAY_REFUNDED = "REFUNDED";

    /** 业务风险等级 */
    public static final String RISK_LOW = "LOW";
    public static final String RISK_MIDDLE = "MIDDLE";
    public static final String RISK_HIGH = "HIGH";

    /** 默认分佣比例 30% */
    public static final double DEFAULT_COMMISSION_RATE = 0.30;

}
