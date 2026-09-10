package com.ruoyi.opc.notification.mapper;

import com.ruoyi.opc.notification.domain.NotificationInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内信收件箱 Mapper（vanilla MyBatis）
 *
 * @author OAC
 */
@Mapper
public interface NotificationInboxMapper {
    int insert(NotificationInbox record);

    NotificationInbox selectById(Long id);

    int updateById(NotificationInbox record);

    int deleteById(Long id);

    List<NotificationInbox> selectList(NotificationInbox record);

    Long selectUnreadCount(@Param("userId") Long userId);

    List<NotificationInbox> selectInboxPage(@Param("userId") Long userId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);
}
