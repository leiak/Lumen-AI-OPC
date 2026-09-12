package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpReturn;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface OpcErpReturnMapper {
    int insert(OpcErpReturn ret);

    int updateById(OpcErpReturn ret);

    int deleteById(@Param("id") Long id, @Param("companyId") Long companyId);

    OpcErpReturn selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    List<OpcErpReturn> selectList(@Param("companyId") Long companyId,
                                  @Param("returnType") String returnType,
                                  @Param("status") String status,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    int countList(@Param("companyId") Long companyId,
                  @Param("returnType") String returnType,
                  @Param("status") String status);

    OpcErpReturn selectByReturnNo(@Param("companyId") Long companyId,
                                  @Param("returnNo") String returnNo);

    List<OpcErpReturn> selectListByType(@Param("companyId") Long companyId,
                                        @Param("returnType") String returnType);

    int updateStatus(@Param("id") Long id,
                     @Param("companyId") Long companyId,
                     @Param("status") String status);

    /**
     * 统计今日退货单数,用于 return_no 序号生成
     */
    Integer countTodayReturns(@Param("companyId") Long companyId,
                              @Param("today") LocalDate today);
}
