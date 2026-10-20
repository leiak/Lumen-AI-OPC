package com.ruoyi.opc.agent.service.impl;

import com.ruoyi.opc.agent.domain.OpcAgentDefinition;
import com.ruoyi.opc.agent.domain.OpcAgentInstance;
import com.ruoyi.opc.agent.mapper.OpcAgentDefinitionMapper;
import com.ruoyi.opc.agent.mapper.OpcAgentInstanceMapper;
import com.ruoyi.opc.agent.service.IOpcAgentInstanceService;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static com.ruoyi.opc.common.constant.OpcConstants.*;

/**
 * Agent 实例服务实现
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcAgentInstanceServiceImpl implements IOpcAgentInstanceService {

    private final OpcAgentInstanceMapper instanceMapper;
    private final OpcAgentDefinitionMapper definitionMapper;

    @Override
    public OpcAgentInstance getById(Long id) {
        return instanceMapper.selectById(id);
    }

    @Override
    public OpcAgentInstance getByIdAndCompany(Long id, Long companyId) {
        return instanceMapper.selectByIdAndCompany(id, companyId);
    }

    @Override
    public OpcAgentInstance getByCode(String code) {
        return instanceMapper.selectByCode(code);
    }

    @Override
    public List<OpcAgentInstance> listByCompany(Long companyId) {
        return instanceMapper.selectByCompany(companyId);
    }

    @Override
    public List<OpcAgentInstance> listRunningByUser(Long userId) {
        return instanceMapper.selectRunningByUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpcAgentInstance hire(Long companyId, Long definitionId, String hireType, Integer duration, String nickname) {
        OpcAgentDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) throw new OpcException("Agent 定义不存在");
        if (!"ACTIVE".equals(def.getStatus())) throw new OpcException("Agent 未上架");

        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setInstanceCode(OpcCodeGenerator.instanceCode());
        inst.setCompanyId(companyId);
        inst.setDefinitionId(definitionId);
        inst.setNickname(nickname != null ? nickname : def.getName());
        inst.setHireType(hireType);
        inst.setHireDuration(duration == null ? 1 : duration);
        inst.setStartTime(new Date());
        inst.setExpireTime(calcExpire(hireType, duration == null ? 1 : duration));
        inst.setAutoRenew(0);
        inst.setMemoryNamespace("mem_" + inst.getInstanceCode());
        inst.setTokenUsed(0L);
        inst.setTokenQuota(def.getFreeQuota() == null ? 5000L : def.getFreeQuota().longValue());
        inst.setTaskCount(0);
        inst.setStatus("RUNNING");
        inst.setCreateBy("system");
        instanceMapper.insert(inst);

        // 累加下载数
        definitionMapper.incrementDownloads(definitionId);

        log.info("[AgentInstance] 雇佣成功 instanceCode={} company={} agent={}",
                inst.getInstanceCode(), companyId, def.getName());
        return inst;
    }

    @Override
    public int pause(Long id, Long companyId, String operatorId) {
        return updateStatusScoped(id, companyId, operatorId, "PAUSED");
    }

    @Override
    public int resume(Long id, Long companyId, String operatorId) {
        return updateStatusScoped(id, companyId, operatorId, "RUNNING");
    }

    @Override
    public int revoke(Long id, Long companyId, String operatorId) {
        return updateStatusScoped(id, companyId, operatorId, "REVOKED");
    }

    /**
     * 租户作用域的状态变更。Mapper 在 {@code WHERE} 同时约束 id 和 company_id,
     * 因此 {@code rowsAffected == 0} 一律按「不存在 / 不属于该租户」处理 —— 抛 403。
     *
     * <p>为何不"先 load 再 check 再 update":有 TOCTOU 窗口 —— 在 load 与 update 之间
     * 另一笔事务可能已删除/转让该实例;此外 load 本身也会泄漏"该 id 存在"的信息给攻击者,
     * 而我们希望对越权请求统一返回 403。</p>
     */
    private int updateStatusScoped(Long id, Long companyId, String operatorId, String newStatus) {
        if (id == null || companyId == null) {
            throw new OpcException(400, "实例 id 与租户 id 必填", "AGENT");
        }
        int rows = instanceMapper.updateStatusByCompany(id, companyId, newStatus,
                operatorId == null ? "system" : operatorId);
        if (rows == 0) {
            throw new OpcException(403, "无权操作该实例", "AGENT");
        }
        return rows;
    }

    @Override
    public void addTokenUsed(Long id, long delta) {
        instanceMapper.addTokenUsed(id, delta);
    }

    @Override
    public void incrementTaskCount(Long id) {
        instanceMapper.incrementTaskCount(id);
    }

    private Date calcExpire(String hireType, int duration) {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime expire;
        switch (hireType) {
            case HIRE_QUARTERLY -> expire = start.plusMonths(3L * duration);
            case HIRE_YEARLY -> expire = start.plusYears(duration);
            case HIRE_TRIAL -> expire = start.plusDays(7L * duration);
            default -> expire = start.plusMonths(duration);
        }
        return Date.from(expire.atZone(ZoneId.systemDefault()).toInstant());
    }

}
