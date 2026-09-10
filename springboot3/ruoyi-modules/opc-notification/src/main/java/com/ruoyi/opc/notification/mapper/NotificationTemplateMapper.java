package com.ruoyi.opc.notification.mapper;

import com.ruoyi.opc.notification.domain.NotificationTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知模板 Mapper（vanilla MyBatis）
 *
 * @author OAC
 */
@Mapper
public interface NotificationTemplateMapper {
    int insert(NotificationTemplate record);

    NotificationTemplate selectById(Long id);

    int updateById(NotificationTemplate record);

    int deleteById(Long id);

    List<NotificationTemplate> selectList(NotificationTemplate record);

    NotificationTemplate selectByCodeAndVersion(@Param("code") String code,
                                                @Param("version") Integer version);

    NotificationTemplate selectLatestEnabled(@Param("code") String code);
}
