package com.ruoyi.opc.agent.mapper;

import com.ruoyi.opc.agent.domain.OpcAgentInstance;

import java.util.List;

/**
 * Agent 实例 Mapper
 *
 * @author OAC
 */
public interface OpcAgentInstanceMapper {

    OpcAgentInstance selectById(Long id);

    OpcAgentInstance selectByIdAndCompany(Long id, Long companyId);

    OpcAgentInstance selectByCode(String instanceCode);

    List<OpcAgentInstance> selectByCompany(Long companyId);

    List<OpcAgentInstance> selectRunningByUser(Long userId);

    int insert(OpcAgentInstance record);

    int update(OpcAgentInstance record);

    /**
     * 租户作用域的状态更新。{@code WHERE id = #{id} AND company_id = #{companyId}}
     * —— {@code rowsAffected == 0} 既代表「不存在」也代表「不属于该租户」,
     * 调用方按 403 处理。Race-free,无需 load-then-check。
     */
    int updateStatusByCompany(Long id, Long companyId, String status, String updateBy);

    int incrementTaskCount(Long id);

    int addTokenUsed(Long id, Long delta);

}
