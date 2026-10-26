package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.common.redis.service.RedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LlmCache} 的租户隔离回归测试 —— 3b
 *
 * <p>缓存 key 以前只由 messages + options 决定 = <b>全平台共用一个缓存池</b>。
 * 后果：谁触发的 provider 调用谁付钱，但复用的人不付 ——
 * A 公司付的钱让 B 公司白嫖，而且 B 的用量数字取决于 A 当天跑了多少次。</p>
 *
 * <p>修法：companyId 进 key，缓存只在同一家公司内部复用。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCache 租户隔离 单测")
class LlmCacheTest {

    @Mock
    private RedisService redisService;

    private LlmCache cache() {
        return new LlmCache(redisService);
    }

    private List<ChatMessage> messages() {
        return List.of(ChatMessage.user("同样的问题"));
    }

    @Test
    @DisplayName("同公司同 prompt → 同一个 key (缓存能命中)")
    void sameCompany_sameKey() {
        LlmCache cache = cache();

        String k1 = cache.buildKey(messages(), null, 1L);
        String k2 = cache.buildKey(messages(), null, 1L);

        assertThat(k1).isEqualTo(k2);
    }

    @Test
    @DisplayName("不同公司同 prompt → 不同 key (不跨租户复用)")
    void differentCompany_differentKey() {
        LlmCache cache = cache();

        String k1 = cache.buildKey(messages(), null, 1L);
        String k2 = cache.buildKey(messages(), null, 2L);

        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("companyId=null → 退回共享命名空间(不是各算各的)")
    void nullCompany_sharedNamespace() {
        LlmCache cache = cache();

        String k1 = cache.buildKey(messages(), null, null);
        String k2 = cache.buildKey(messages(), null, null);

        assertThat(k1).isEqualTo(k2);
        assertThat(k1).isNotEqualTo(cache.buildKey(messages(), null, 1L));
    }
}
