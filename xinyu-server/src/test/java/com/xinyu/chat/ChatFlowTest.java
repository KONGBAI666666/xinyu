package com.xinyu.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.character.entity.AiCharacter;
import com.xinyu.character.service.CharacterService;
import com.xinyu.common.security.JwtUtil;
import com.xinyu.conversation.entity.Conversation;
import com.xinyu.conversation.service.ConversationService;
import com.xinyu.llm.mock.MockLlmClient;
import com.xinyu.message.entity.Message;
import com.xinyu.message.enums.MessageRole;
import com.xinyu.message.enums.MessageStatus;
import com.xinyu.message.service.MessageService;
import com.xinyu.user.entity.User;
import com.xinyu.user.service.UserService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-3 聊天核心验收测试（真实 MySQL, 走完整 Filter + Interceptor 链路）
 *
 * <p>覆盖: 会话创建(greeting落库) / 消息历史 / SSE 事件流(meta→delta→done) /
 * 消息状态机(COMPLETED/FAILED) / LLM 异常(event:error + 51001) / 越权与鉴权。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ChatFlowTest {

    private static final String GREETING = "你好，我是心屿的测试角色，很高兴见到你。";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private CharacterService characterService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private String token;
    private Long otherUserId;
    private String otherToken;
    private Long characterId;
    private Long conversationId;

    @BeforeAll
    void setUp() {
        long stamp = System.currentTimeMillis() % 100_000_000;
        userId = createUser("ct_" + stamp);
        token = jwtUtil.generate(userId, "ct_" + stamp);
        otherUserId = createUser("cx_" + stamp);
        otherToken = jwtUtil.generate(otherUserId, "cx_" + stamp);

        AiCharacter character = new AiCharacter();
        character.setName("测试屿屿");
        character.setIntro("M1-3 验收专用测试角色");
        character.setSystemPrompt("你是心屿的测试角色，温柔友善。");
        character.setGreeting(GREETING);
        character.setCreatorId(userId);
        character.setCreatorType("OFFICIAL");
        character.setStatus("PUBLISHED");
        characterService.save(character);
        characterId = character.getId();
    }

    @AfterAll
    void tearDown() {
        // 物理删除测试数据（绕过逻辑删除, 不留脏数据）
        jdbcTemplate.update("DELETE FROM message WHERE user_id IN (?, ?)", userId, otherUserId);
        jdbcTemplate.update("DELETE FROM conversation WHERE user_id IN (?, ?)", userId, otherUserId);
        jdbcTemplate.update("DELETE FROM `character` WHERE id = ?", characterId);
        jdbcTemplate.update("DELETE FROM `user` WHERE id IN (?, ?)", userId, otherUserId);
    }

    @Test
    @Order(1)
    @DisplayName("创建会话: conversation 落库 + greeting 为首条 ASSISTANT 消息")
    void createConversation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\":\"" + characterId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.title").value("测试屿屿"))
                .andExpect(jsonPath("$.data.lastMessagePreview").value(GREETING))
                .andReturn();

        conversationId = Long.parseLong(objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText());

        Conversation saved = conversationService.getById(conversationId);
        assertNotNull(saved);
        assertEquals(userId, saved.getUserId());

        List<Message> messages = messageService.listRecent(conversationId, 10);
        assertEquals(1, messages.size());
        Message greeting = messages.get(0);
        assertEquals(MessageRole.ASSISTANT, greeting.getMessageType());
        assertEquals(MessageStatus.COMPLETED, greeting.getStatus());
        assertEquals(GREETING, greeting.getContent());
        assertEquals(1, greeting.getSequenceNo());
    }

    @Test
    @Order(2)
    @DisplayName("创建会话: 角色不存在 40400")
    void createConversationCharacterNotFound() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("角色不存在或不可用"));
    }

    @Test
    @Order(3)
    @DisplayName("消息历史: 仅 greeting 一条, ID 序列化为字符串")
    void listMessagesOnlyGreeting() throws Exception {
        mockMvc.perform(get("/api/conversations/{id}/messages", conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].messageType").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[0].content").value(GREETING));
    }

    @Test
    @Order(4)
    @DisplayName("SSE 聊天: meta→delta→done 事件流 + 双消息落库 COMPLETED")
    void chatStreaming() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations/{id}/chat", conversationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\",\"clientMessageId\":\"cmid-1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitSse(result, 15_000);
        assertTrue(body.contains("event:meta"), "应包含 meta 事件: " + body);
        assertTrue(body.contains("event:delta"), "应包含 delta 事件: " + body);
        assertTrue(body.contains("event:done"), "应包含 done 事件: " + body);
        assertTrue(body.contains("\"userMessageId\""), "meta 应含 userMessageId");
        assertTrue(body.contains("\"assistantMessageId\""), "meta 应含 assistantMessageId");

        // 数据库: USER + ASSISTANT 各一条, ASSISTANT 为完整拼接文本且 COMPLETED
        List<Message> messages = messageService.listRecent(conversationId, 10);
        assertEquals(3, messages.size());
        Message userMsg = messages.get(1);
        assertEquals(MessageRole.USER, userMsg.getMessageType());
        assertEquals("你好", userMsg.getContent());
        assertEquals(MessageStatus.COMPLETED, userMsg.getStatus());

        Message assistantMsg = messages.get(2);
        assertEquals(MessageRole.ASSISTANT, assistantMsg.getMessageType());
        assertEquals(MessageStatus.COMPLETED, assistantMsg.getStatus());
        assertTrue(assistantMsg.getContent().startsWith("你好呀，"), "应保存完整拼接文本");
        assertNotNull(assistantMsg.getCompletionTokens());
        assertTrue(assistantMsg.getCompletionTokens() > 0);
        assertEquals(userMsg.getId(), assistantMsg.getParentMessageId());

        // 会话冗余字段: 标题改为首条用户消息, 摘要为最新 AI 回复
        Conversation conversation = conversationService.getById(conversationId);
        assertEquals("你好", conversation.getTitle());
        assertEquals(assistantMsg.getContent(), conversation.getLastMessagePreview());
    }

    @Test
    @Order(5)
    @DisplayName("SSE 聊天: LLM 失败 → event:error(51001) + 消息置 FAILED")
    void chatLlmFailure() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations/{id}/chat", conversationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"触发失败 " + MockLlmClient.FAIL_TRIGGER + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitSse(result, 15_000);
        assertTrue(body.contains("event:error"), "应包含 error 事件: " + body);
        assertTrue(body.contains("51001"), "error 事件应携带 51001: " + body);

        List<Message> messages = messageService.listRecent(conversationId, 10);
        Message last = messages.get(messages.size() - 1);
        assertEquals(MessageRole.ASSISTANT, last.getMessageType());
        assertEquals(MessageStatus.FAILED, last.getStatus());
    }

    @Test
    @Order(6)
    @DisplayName("鉴权: 无 token 访问聊天接口 40100")
    void chatWithoutToken() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\":\"" + characterId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @Order(7)
    @DisplayName("越权: 他人会话按 40400 处理, 不暴露资源存在性")
    void listMessagesOfOthersConversation() throws Exception {
        mockMvc.perform(get("/api/conversations/{id}/messages", conversationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("会话不存在"));
    }

    @Test
    @Order(8)
    @DisplayName("参数校验: 空消息内容 42200")
    void chatBlankContent() throws Exception {
        mockMvc.perform(post("/api/conversations/{id}/chat", conversationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(42200));
    }

    @Test
    @Order(9)
    @DisplayName("会话列表: 仅返回本人会话, 按最新消息时间倒序")
    void listConversations() throws Exception {
        // 再建一个会话: 其 greeting 时间比首个会话的最后一条消息更晚, 应排在列表首位;
        // last_message_at 是 DATETIME(秒级), 先等 1.1s 避免与前用例同秒导致排序断言不稳定
        Thread.sleep(1100);
        MvcResult created = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\":\"" + characterId + "\",\"title\":\"第二个会话\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String secondId = objectMapper
                .readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(secondId))
                .andExpect(jsonPath("$.data[0].title").value("第二个会话"))
                .andExpect(jsonPath("$.data[1].id").value(String.valueOf(conversationId)));

        // 用户隔离: 他人拿到空列表而非别人的会话
        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /**
     * 轮询等待 SSE 流结束标记（done/error）出现, MockHttpServletResponse
     * 的缓冲区随 emitter.send 增长, 无需真实网络
     */
    private String awaitSse(MvcResult result, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:done") || body.contains("event:error")) {
                // 终态事件后 DB 更新已完成(先落库再推送), 稍等确保 emitter.complete
                Thread.sleep(200);
                return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            }
            Thread.sleep(100);
        }
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Long createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$test.placeholder.hash.not.used.in.chat");
        user.setNickname(username);
        userService.save(user);
        return user.getId();
    }
}
