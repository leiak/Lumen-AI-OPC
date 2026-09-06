package com.ruoyi.opc.ai.security;

import com.ruoyi.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 限流器（基于 Redis 滑动窗口）
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final RedisService redisService;

    /**
     * 限流判断
     * @param key 限流 key（如 user:123 / agent:456 / global）
     * @param maxRequests 时间窗口内最大请求数
     * @param windowSeconds 时间窗口（秒）
     * @return true 放行，false 拒绝
     */
    public boolean allow(String key, int maxRequests, int windowSeconds) {
        String fullKey = "opc:rate:" + key;
        try {
            Long count = redisService.redisTemplate.opsForValue().increment(fullKey, 1L);
            if (count != null && count == 1L) {
                redisService.expire(fullKey, windowSeconds);
            }
            return count == null || count <= maxRequests;
        } catch (Exception e) {
            log.warn("限流检查失败 key={}", fullKey, e);
            return true;
        }
    }

}
