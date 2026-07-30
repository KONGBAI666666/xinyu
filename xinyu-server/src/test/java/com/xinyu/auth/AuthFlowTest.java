package com.xinyu.auth;

import com.xinyu.user.entity.User;
import com.xinyu.user.service.UserService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-2 验收测试: 注册/登录/当前用户/修改密码 全流程
 *
 * <p>走真实链路: MockMvc → JwtAuthFilter → AuthInterceptor → Controller → 真实 MySQL。
 * 测试账号用时间戳后缀避免冲突, 结束后物理删除(不留逻辑删除脏数据)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

    /** 测试用户名: it_+8位时间戳, 共11位, 符合4-16位规则 */
    private static final String USERNAME = "it_" + (System.currentTimeMillis() % 100_000_000L);

    private static final String PASSWORD = "pass123456";

    private static final String NEW_PASSWORD = "newpass654321";

    /** 注册后保存的 token, 供后续用例复用 */
    private static String token;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    void cleanUp() {
        // 物理删除测试账号(绕过逻辑删除), 保持数据库干净
        jdbcTemplate.update("DELETE FROM `user` WHERE username = ?", USERNAME);
    }

    @Test
    @Order(1)
    @DisplayName("注册成功: 返回token+user, 库中存BCrypt哈希而非明文")
    void registerSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","nickname":"验收用户"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(USERNAME))
                .andExpect(jsonPath("$.data.user.nickname").value("验收用户"))
                // 雪花ID序列化为字符串, 防JS丢精度
                .andExpect(jsonPath("$.data.user.id").isString())
                .andReturn();

        token = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.data.token");
        assertNotNull(token);

        // 直查数据库验证密码存储
        User dbUser = userService.getByUsername(USERNAME);
        assertNotNull(dbUser, "数据库应新增用户");
        assertNotEquals(PASSWORD, dbUser.getPassword(), "严禁明文存密码");
        assertTrue(dbUser.getPassword().startsWith("$2"), "应为BCrypt哈希($2a$10$...)");
        assertNotNull(dbUser.getLastLoginAt(), "注册即登录, 应记录登录时间");
        System.out.println("[验收-注册] password_hash = " + dbUser.getPassword());
    }

    @Test
    @Order(2)
    @DisplayName("重复注册: 40100 用户名已存在")
    void duplicateRegister() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"other123"}
                                """.formatted(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @Order(3)
    @DisplayName("参数校验: 用户名不合规 → 42200")
    void invalidUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"pass123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(42200));
    }

    @Test
    @Order(4)
    @DisplayName("登录成功: 正确密码返回token")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(USERNAME));
    }

    @Test
    @Order(5)
    @DisplayName("登录失败: 密码错误与用户不存在返回同一文案(防枚举)")
    void loginFailUnified() throws Exception {
        // 密码错误
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"wrong_pass"}
                                """.formatted(USERNAME)))
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        // 用户不存在: 文案必须与密码错误完全一致
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no_such_user","password":"whatever"}
                                """))
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @Order(6)
    @DisplayName("无token访问 /api/users/me → 40100")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @Order(7)
    @DisplayName("带token访问 /api/users/me → 返回用户信息且无password字段")
    void meWithToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @Order(8)
    @DisplayName("修改密码: 旧密码错误 → 40100")
    void updatePasswordWrongOld() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrong_old","newPassword":"%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("原密码错误"));
    }

    @Test
    @Order(9)
    @DisplayName("修改密码成功: 旧密码失效, 新密码可登录")
    void updatePasswordSuccess() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(jsonPath("$.code").value(0));

        // 旧密码登录失败
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(jsonPath("$.code").value(40100));

        // 新密码登录成功
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, NEW_PASSWORD)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
        System.out.println("[验收-改密] 旧密码已失效, 新密码登录成功 ✓");
    }
}
