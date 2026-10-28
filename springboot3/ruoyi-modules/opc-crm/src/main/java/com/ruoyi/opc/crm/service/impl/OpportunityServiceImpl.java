package com.ruoyi.opc.crm.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.crm.domain.CrmCustomer;
import com.ruoyi.opc.crm.domain.CrmOpportunity;
import com.ruoyi.opc.crm.dto.OpportunityScoreResponse;
import com.ruoyi.opc.crm.enums.OpportunityStage;
import com.ruoyi.opc.crm.gateway.AiCoreGateway;
import com.ruoyi.opc.crm.mapper.CrmCustomerMapper;
import com.ruoyi.opc.crm.mapper.CrmOpportunityMapper;
import com.ruoyi.opc.crm.service.CrmOwnership;
import com.ruoyi.opc.crm.service.OpportunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityServiceImpl implements OpportunityService {

    private final CrmOpportunityMapper mapper;
    private final CrmCustomerMapper customerMapper;
    private final AiCoreGateway aiCoreGateway;

    /** 评分用的 JSON 解析器（只解析 LLM 的输出，不参与业务对象序列化）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 评分场景标识 —— opc-ai-core 按它把用量归到租户账上。 */
    private static final String SCENE_SCORE = "crm_opportunity_score";

    /** score_reason 列是 varchar(1024)，留一半余量给多字节字符。 */
    private static final int REASON_MAX_LEN = 500;

    /** 模型没按 JSON 输出时的兜底：从正文里捞一个「评分:85」这样的数字。 */
    private static final Pattern SCORE_PATTERN =
            Pattern.compile("(?i)(?:score|评分|得分|分数)[^0-9]{0,12}(\\d{1,3})");

    /** 整个正文就是一个数字的情况。 */
    private static final Pattern BARE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d{1,3})\\s*$");

    /** 评分指令。模型必须只吐 JSON —— 但下面还有两层兜底，所以它不听话也不会炸。 */
    private static final String SCORE_SYSTEM_PROMPT = """
            你是一名资深销售顾问。请根据给定的商机与客户信息，评估这条商机的成交可能性。

            只输出一个 JSON 对象，不要输出任何解释性文字，也不要用 Markdown 代码块：
            {"score": <0 到 100 的整数>, "reason": "<一句话理由，不超过 60 个字>"}

            评分标准：
            - 80-100：需求明确、预算到位、成交概率很高
            - 60-79：需求真实，但仍有明显的不确定因素
            - 40-59：需求偏弱，或关键信息缺失
            - 0-39：线索价值很低
            """;

    @Override
    @Transactional
    public CrmOpportunity create(CrmOpportunity opportunity, Long operatorId) {
        if (opportunity.getCustomerId() == null) {
            throw new IllegalArgumentException("customerId required");
        }
        if (opportunity.getName() == null || opportunity.getName().isBlank()) {
            throw new IllegalArgumentException("Opportunity name cannot be empty (商机名称不能为空)");
        }
        // 商机挂在别人客户下 = 越权写入
        CrmOwnership.requireCustomer(customerMapper, opportunity.getCustomerId(), operatorId);
        if (opportunity.getStage() == null) {
            opportunity.setStage(OpportunityStage.LEAD.name());
        }
        if (opportunity.getScore() == null) opportunity.setScore(0);
        // 归属由服务端定 —— 客户端给的 ownerId 不作数
        // (否则可以把商机"挂"到别人名下, 或者把自己摘出去躲审计)
        opportunity.setOwnerId(operatorId);
        opportunity.setCreateBy(String.valueOf(operatorId));
        opportunity.setUpdateBy(String.valueOf(operatorId));
        if (opportunity.getDeleted() == null) opportunity.setDeleted(0);
        mapper.insert(opportunity);
        log.info("Created opportunity id={} name={} stage={}",
            opportunity.getId(), opportunity.getName(), opportunity.getStage());
        return opportunity;
    }

    @Override
    @Transactional
    public CrmOpportunity update(Long id, CrmOpportunity update, Long operatorId) {
        CrmOpportunity existing = mapper.selectByIdAndOwner(id, operatorId);
        if (existing == null) {
            throw CrmOwnership.denied("商机");
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getAmount() != null) existing.setAmount(update.getAmount());
        if (update.getExpectedClose() != null) existing.setExpectedClose(update.getExpectedClose());
        // 归属**不能**由客户端改 —— 原来这行让 request 里的 ownerId 直接覆盖,
        // 等于可以把自己的商机转给别人、或把别人的商机抢过来。
        existing.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public CrmOpportunity getById(Long id, Long ownerId) {
        CrmOpportunity o = mapper.selectByIdAndOwner(id, ownerId);
        if (o == null) {
            throw CrmOwnership.denied("商机");
        }
        return o;
    }

    @Override
    @Transactional
    public CrmOpportunity changeStage(Long id, OpportunityStage target, String reason, Long operatorId) {
        CrmOpportunity opp = mapper.selectByIdAndOwner(id, operatorId);
        if (opp == null) {
            throw CrmOwnership.denied("商机");
        }
        OpportunityStage current = OpportunityStage.valueOf(opp.getStage());
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("Invalid stage transition: %s → %s (商机阶段不能从 %s 跳到 %s)",
                    current, target, current, target));
        }
        opp.setStage(target.name());
        if (target == OpportunityStage.LOST) {
            opp.setLostReason(reason);
        }
        opp.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(opp);
        log.info("Opportunity id={} stage changed {} → {} by operator {}",
            id, current, target, operatorId);
        return opp;
    }

    /**
     * AI 商机评分 —— 真的调一次 LLM，把分数**和理由**一起写回商机。
     *
     * <p>此前这个方法调的是一个从没被实现过的地址（见 {@link AiCoreGateway} 的类注释），
     * 所以前端看板上的「LLM 评分」按钮点了必定报错；而看板卡片上显示的那些分数
     * （85 / 70 / 95 …）全是种子 SQL 写死的假数 —— 分数有、理由是空的，
     * 而系统里没有任何一条代码路径能产出这种「有分没理由」的记录。
     *
     * <p>现在的规矩：<b>分数和理由同生共死</b>。模型给了分就给理由，
     * 理由就用模型原话；模型没给出理由，就把它的原话当理由存下来。
     * 解析不出来 → 报错，**不写库** —— 宁可这次没分，也不能在卡上留一个
     * 来源不明的数字。
     */
    @Override
    @Transactional
    public OpportunityScoreResponse score(Long id, Long operatorId) {
        CrmOpportunity opp = mapper.selectByIdAndOwner(id, operatorId);
        if (opp == null) {
            throw CrmOwnership.denied("商机");
        }
        // 客户也要是本人的 —— 否则 AI 打分会把别人的客户资料带出去
        CrmCustomer customer = customerMapper.selectByIdAndOwner(opp.getCustomerId(), operatorId);
        if (customer == null) {
            throw CrmOwnership.denied("客户");
        }

        R<Map<String, Object>> resp = aiCoreGateway.chat(buildScorePayload(opp, customer));
        if (resp == null) {
            throw new OpcException(503, "AI 评分服务返回空响应，请稍后重试", "CRM");
        }
        if (!R.isSuccess(resp)) {
            String msg = (resp.getMsg() == null || resp.getMsg().isBlank())
                    ? "AI 评分服务暂时不可用，请稍后重试"
                    : resp.getMsg();
            throw new OpcException(503, msg, "CRM");
        }

        OpportunityScoreResponse parsed = parseScore(extractContent(resp.getData()));

        opp.setScore(parsed.getScore());
        opp.setScoreReason(parsed.getReason());
        opp.setUpdateBy(String.valueOf(operatorId));
        mapper.updateById(opp);
        log.info("Opportunity id={} scored by LLM: score={} operator={}", id, parsed.getScore(), operatorId);
        return parsed;
    }

    // ============================================================
    // AI 评分：prompt 组装 + 返回解析
    // ============================================================

    /**
     * 组装评分用的 prompt。
     *
     * <p>指令放 system、业务数据放 user：对端 opc-ai-core 只对 role=USER 的消息做
     * Prompt 注入检测（{@code OpcLlmController#guardUserMessages}），客户名、商机名
     * 这些用户可写的内容落在 user 里，被判成注入就直接 403 —— 那是安全策略该有的
     * 行为，总比让一句「忽略以上指令」把 system prompt 顶掉强。
     */
    private Map<String, Object> buildScorePayload(CrmOpportunity opp, CrmCustomer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("请给下面这条商机打分。\n");
        sb.append("商机名称：").append(nz(opp.getName())).append('\n');
        sb.append("商机金额（元）：")
          .append(opp.getAmount() == null ? "未知" : opp.getAmount().toPlainString()).append('\n');
        sb.append("当前阶段：").append(nz(opp.getStage())).append('\n');
        sb.append("预计成交日期：")
          .append(opp.getExpectedClose() == null ? "未知" : opp.getExpectedClose().toString()).append('\n');
        sb.append("客户名称：").append(nz(customer.getName())).append('\n');
        sb.append("客户级别：").append(nz(customer.getLevel())).append('\n');
        sb.append("客户来源：").append(nz(customer.getSource())).append('\n');
        sb.append("客户标签：").append(nz(customer.getTags())).append('\n');
        sb.append("只输出 JSON，不要输出别的文字。");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", SCORE_SYSTEM_PROMPT));
        messages.add(message("user", sb.toString()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", SCENE_SCORE);
        payload.put("temperature", 0.2);
        payload.put("maxTokens", 500);
        payload.put("messages", messages);
        return payload;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /** 从 opc-ai-core 的返回体里取模型输出文本（ChatResponse.content）。 */
    private static String extractContent(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object content = data.get("content");
        return content == null ? null : content.toString();
    }

    /**
     * 解析模型输出 → {@code {score, reason}}。
     *
     * <p>三层兜底，逐层放宽，但<b>任何一层都解析不出分数就报错</b>，
     * 绝不「猜一个」：
     * <ol>
     *   <li>剥掉 Markdown 代码块后按 JSON 解析（模型被要求只吐 JSON，绝大多数走这条）；</li>
     *   <li>JSON 里没有可用的 score → 正则捞「评分:85」；</li>
     *   <li>整个正文就是一个数 → 当分数用。</li>
     * </ol>
     *
     * <p>理由：JSON 里的 {@code reason}；没有就把模型原话（截断）当理由 ——
     * <b>有分数就必须有理由</b>，这是「有分没理由 = 编的」这条不变式的另一半。
     */
    private OpportunityScoreResponse parseScore(String content) {
        if (content == null || content.isBlank()) {
            throw new OpcException(502, "AI 评分返回空内容，请重试", "CRM");
        }
        String text = stripCodeFence(content);

        Integer score = null;
        String reason = null;

        Map<String, Object> map = tryParseJson(text);
        if (map != null) {
            score = toScore(map.get("score"));
            Object rawReason = map.get("reason");
            if (rawReason != null && !rawReason.toString().isBlank()) {
                reason = truncate(rawReason.toString().trim());
            }
        }
        if (score == null) {
            score = extractScoreByPattern(text);
        }
        if (score == null) {
            throw new OpcException(502, "AI 评分结果无法解析，请重试", "CRM");
        }
        if (reason == null) {
            // 模型没给理由字段 → 把它自己说的话原样留档，至少来源可追
            reason = truncate(text);
        }
        return OpportunityScoreResponse.builder().score(score).reason(reason).build();
    }

    /** 剥掉 ```json ... ``` 代码块。 */
    private static String stripCodeFence(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    /** 解析失败返回 null（不抛 —— 后面还有正则兜底）。 */
    private static Map<String, Object> tryParseJson(String text) {
        try {
            return MAPPER.readValue(text, new TypeReference<>() { });
        } catch (Exception e) {
            log.warn("[opc-crm] LLM 返回非 JSON：{}", e.getMessage());
            return null;
        }
    }

    /** score 可能是数字 / 数字字符串；越界夹断到 [0, 100]。 */
    private static Integer toScore(Object raw) {
        if (raw == null) {
            return null;
        }
        Integer parsed;
        if (raw instanceof Number n) {
            parsed = n.intValue();
        } else {
            try {
                parsed = Integer.valueOf(raw.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return Math.max(0, Math.min(100, parsed));
    }

    /** 正文里捞分数：「评分:85」「score = 92」「85」都行。 */
    private static Integer extractScoreByPattern(String text) {
        Matcher m = SCORE_PATTERN.matcher(text);
        if (m.find()) {
            return Math.max(0, Math.min(100, Integer.parseInt(m.group(1))));
        }
        m = BARE_NUMBER_PATTERN.matcher(text);
        if (m.matches()) {
            return Math.max(0, Math.min(100, Integer.parseInt(m.group(1))));
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= REASON_MAX_LEN ? s : s.substring(0, REASON_MAX_LEN);
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "未知" : s;
    }

    @Override
    public List<CrmOpportunity> listByCustomer(Long customerId, Long ownerId) {
        return mapper.selectByCustomerIdAndOwner(customerId, ownerId);
    }

    @Override
    public List<CrmOpportunity> listByStage(OpportunityStage stage, Long ownerId) {
        return mapper.selectByStageAndOwner(stage.name(), ownerId);
    }

    @Override
    public List<CrmOpportunity> list(Long ownerId, Long customerId, OpportunityStage stage) {
        // ownerId 一定会带上, 所以 selectList 里的 owner 过滤永远生效 ——
        // 三个条件(customerId / stage / owner)是在同一个人名下筛, 不是三个独立维度。
        return mapper.selectList(ownerId, stage == null ? null : stage.name(), customerId);
    }
}
