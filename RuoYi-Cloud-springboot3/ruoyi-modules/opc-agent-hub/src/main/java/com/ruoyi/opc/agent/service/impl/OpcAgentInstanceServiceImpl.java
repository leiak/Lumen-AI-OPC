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
    public int pause(Long id) {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(id);
        inst.setStatus("PAUSED");
        inst.setUpdateBy("system");
        return instanceMapper.update(inst);
    }

    @Override
    public int resume(Long id) {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(id);
        inst.setStatus("RUNNING");
        inst.setUpdateBy("system");
        return instanceMapper.update(inst);
    }

    @Override
    public int revoke(Long id) {
        OpcAgentInstance inst = new OpcAgentInstance();
        inst.setId(id);
        inst.setStatus("REVOKED");
        inst.setUpdateBy("system");
        return instanceMapper.update(inst);
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
