package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpTag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * mp_tag Mapper（只读，标签由种子数据维护）
 *
 * @author OAC
 */
@Mapper
public interface MpTagMapper {
    List<MpTag> selectAll();

    List<MpTag> selectByCategory(String category);
}