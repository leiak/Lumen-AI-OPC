package com.ruoyi.opc.notification.mapper;

import com.ruoyi.opc.notification.domain.NotificationEmailLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 邮件发送日志 Mapper（vanilla MyBatis）
 *
 * @author OAC
 */
@Mapper
public interface NotificationEmailLogMapper {
    int insert(NotificationEmailLog record);

    NotificationEmailLog selectById(Long id);

    int updateById(NotificationEmailLog record);

    int deleteById(Long id);

    List<NotificationEmailLog> selectList(NotificationEmailLog record);
}
