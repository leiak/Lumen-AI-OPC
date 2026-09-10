package com.ruoyi.opc.notification.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket 会话注册表（占位实现）。
 *
 * <p>Task 8 需要在编译期引用该类型，故先提供最小可注入的占位实现：
 * {@link #sendToUser(Long, String)} 仅记录日志，不做实际推送。
 * Task 9 将用「userId -> WebSocketSession 集合」的真实实现整体替换本文件，
 * 只需保持 {@code sendToUser(Long, String)} 签名不变即可。</p>
 *
 * @author OAC
 */
@Slf4j
@Component
public class WsSessionRegistry {

    /**
     * 向指定用户的所有在线 WebSocket 会话推送一条文本消息。
     *
     * @param userId  目标用户
     * @param message 已序列化的消息体（JSON 文本）
     */
    public void sendToUser(Long userId, String message) {
        log.debug("[ws] placeholder registry, drop message for user={} len={}",
                userId, message == null ? 0 : message.length());
    }
}
