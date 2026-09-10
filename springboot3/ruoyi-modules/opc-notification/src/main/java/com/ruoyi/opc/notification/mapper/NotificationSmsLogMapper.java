package com.ruoyi.opc.notification.mapper;

import com.ruoyi.opc.notification.domain.NotificationSmsLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 短信发送日志 Mapper（vanilla MyBatis）
 *
 * @author OAC
 */
@Mapper
public interface NotificationSmsLogMapper {
    int insert(NotificationSmsLog record);

    NotificationSmsLog selectById(Long id);

    int updateById(NotificationSmsLog record);

    int deleteById(Long id);

    List<NotificationSmsLog> selectList(NotificationSmsLog record);
}
