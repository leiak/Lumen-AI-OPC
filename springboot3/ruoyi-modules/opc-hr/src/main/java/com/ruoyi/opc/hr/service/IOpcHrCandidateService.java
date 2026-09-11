package com.ruoyi.opc.hr.service;

import com.ruoyi.opc.hr.domain.OpcHrCandidate;
import com.ruoyi.opc.hr.dto.HrSearchRequest;
import com.ruoyi.opc.hr.dto.HrSearchResult;
import com.ruoyi.opc.hr.dto.OpcHrCandidateDto;

import java.util.List;

public interface IOpcHrCandidateService {

    /**
     * 上传候选人简历(URL 入库,默认 source=MANUAL)
     *
     * @return 新候选人雪花 ID
     */
    Long create(OpcHrCandidateDto dto);

    /**
     * 更新候选人(仅 tags_json + parsed_json,基础信息不变)
     *
     * @return 受影响行数(0 或 1)
     */
    int update(Long id, Long companyId, OpcHrCandidateDto dto);

    /**
     * 删除候选人(硬删除,companyId 隔离)
     */
    int delete(Long id, Long companyId);

    /**
     * 候选人详情
     */
    OpcHrCandidate detail(Long id, Long companyId);

    /**
     * 列表(companyId 隔离,按 create_time DESC)
     */
    List<OpcHrCandidate> list(Long companyId, int offset, int limit);

    /**
     * LLM 解析简历(占位,Task 7 接入 opc-ai-core HttpLlmClient)
     */
    void parse(Long id, Long companyId);

    /**
     * 语义搜索(占位,Task 7 接入 Qdrant 向量召回 + LLM 重排序)
     */
    List<HrSearchResult> search(Long companyId, HrSearchRequest req);
}