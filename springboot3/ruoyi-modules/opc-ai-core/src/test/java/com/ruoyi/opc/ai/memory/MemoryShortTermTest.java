package com.ruoyi.opc.ai.memory;

import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link Memory} 短期记忆（Redis list）回归测试。
 *
 * <p><b>这块以前是空壳</b>：{@code loadShortTerm()} 永远返回空列表，
 * 方法体里写着"简化：实际应使用 LRANGE" —— 多轮对话因此完全没有上下文，
 * 而接口照样返回成功。</p>
 *
 * <p>本测试钉住：
 * <ol>
 *   <li>Redis list 是<b>右追加</b>的，最近的对话在尾部 → 取 {@code LRANGE key -limit -1}，
 *       不是从头取（从头取只会拿到最老的几条，等于把最近的上下文丢掉）；</li>
 *   <li>写进去的必须是能还原回来的 JSON。</li>
 * </ol>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("Memory 短期记忆 单测")
class MemoryShortTermTest {

    private static final String KEY = "opc:memory:short:sess-1";

    @Mock
    private RedisService redisService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ListOperations<String, Object> listOperations;
    @Mock
    private QdrantVectorStore vectorStore;
    @Mock
    private EmbeddingGateway embeddingGateway;

    private Memory memory;

    @BeforeEach
    void setUp() {
        // RedisService.redisTemplate 是 public 字段，直接塞进 mock 里
        redisService.redisTemplate = redisTemplate;
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        memory = new Memory(redisService, vectorStore, embeddingGateway);
        ReflectionTestUtils.setField(memory, "shortTermTtl", 86400L);
    }

    @Test
    @DisplayName("追加短期记忆 → 右追加进 Redis list，并设置过期时间")
    void appendShortTerm_pushesToListAndSetsExpiry() {
        memory.appendShortTerm("sess-1", ChatMessage.user("你好"));

        verify(listOperations).rightPush(eq(KEY), any());
        verify(redisService).expire(KEY, 86400L);
    }

    @Test
    @DisplayName("追加进去的是能还原成 ChatMessage 的 JSON")
    void appendShortTerm_storesSerializableJson() {
        memory.appendShortTerm("sess-1", ChatMessage.user("你好"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(listOperations).rightPush(eq(KEY), captor.capture());

        String json = String.valueOf(captor.getValue());
        assertTrue(json.contains("\"role\":\"USER\""), "JSON 里要带 role：" + json);
        assertTrue(json.contains("你好"), "JSON 里要带 content：" + json);
    }

    @Test
    @DisplayName("加载短期记忆 → 取的是列表尾部（最近 N 条），不是从头取")
    void loadShortTerm_readsTailOfList() {
        String json = "{\"role\":\"USER\",\"content\":\"你好\"}";
        when(listOperations.range(eq(KEY), eq(-2L), eq(-1L))).thenReturn(List.of(json));

        List<ChatMessage> messages = memory.loadShortTerm("sess-1", 2);

        assertEquals(1, messages.size());
        assertEquals(ChatMessage.Role.USER, messages.get(0).getRole());
        assertEquals("你好", messages.get(0).getContent());
        verify(listOperations).range(KEY, -2L, -1L);
    }

    @Test
    @DisplayName("Redis 里没东西 → 返回空列表")
    void loadShortTerm_emptyRedis_returnsEmptyList() {
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        assertTrue(memory.loadShortTerm("sess-1", 5).isEmpty());
    }

    @Test
    @DisplayName("sessionId 为 null 或 limit<=0 → 返回空，且不碰 Redis")
    void loadShortTerm_invalidArgs_returnsEmptyWithoutTouchingRedis() {
        assertTrue(memory.loadShortTerm(null, 5).isEmpty());
        assertTrue(memory.loadShortTerm("sess-1", 0).isEmpty());
        assertTrue(memory.loadShortTerm("sess-1", -1).isEmpty());

        verifyNoInteractions(listOperations);
    }

    @Test
    @DisplayName("Redis 元素是 Map（FastJson2 带类型信息）→ 也能还原")
    void loadShortTerm_toleratesMapShapedElement() {
        String json = "{\"role\":\"ASSISTANT\",\"content\":\"好的\"}";
        when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(Map.of("value", json)));

        List<ChatMessage> messages = memory.loadShortTerm("sess-1", 3);

        assertEquals(1, messages.size());
        assertEquals(ChatMessage.Role.ASSISTANT, messages.get(0).getRole());
        assertEquals("好的", messages.get(0).getContent());
    }

    @Test
    @DisplayName("元素坏了 → 跳过那一条，不让整段上下文崩掉")
    void loadShortTerm_skipsBrokenElement() {
        String json = "{\"role\":\"USER\",\"content\":\"好的\"}";
        when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of("这不是 JSON", json));

        List<ChatMessage> messages = memory.loadShortTerm("sess-1", 3);

        assertEquals(1, messages.size());
        assertEquals("好的", messages.get(0).getContent());
    }

    @Test
    @DisplayName("追加参数为空 → 什么都不做")
    void appendShortTerm_nullArgs_isNoop() {
        memory.appendShortTerm(null, ChatMessage.user("你好"));
        memory.appendShortTerm("sess-1", null);

        verify(listOperations, never()).rightPush(anyString(), any());
    }

    @Test
    @DisplayName("清除短期记忆 → 删掉整个 key")
    void clearShortTerm_deletesKey() {
        memory.clearShortTerm("sess-1");

        verify(redisService).deleteObject(KEY);
    }
}
