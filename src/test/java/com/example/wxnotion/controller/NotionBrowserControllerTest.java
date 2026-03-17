package com.example.wxnotion.controller;

import com.example.wxnotion.AbstractSpringTest;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.NoteAppType;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.NotionService;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class NotionBrowserControllerTest extends AbstractSpringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserConfigRepository userConfigRepository;

    @MockBean
    private NotionService notionService;

    @Value("${security.aesKey}")
    private String aesKey;

    private static final String GUEST_OPEN_ID   = "browser_test_guest";
    private static final String REGULAR_OPEN_ID = "browser_test_regular";
    private static final String TEST_DB_ID      = "browser-test-db-001";
    private static final String TEST_NOTION_KEY = "real_notion_token_xyz";

    private String guestToken;
    private String regularToken;

    @BeforeEach
    void setUp() {
        // 访客用户
        UserConfig guest = new UserConfig();
        guest.setOpenId(GUEST_OPEN_ID);
        guest.setAppType(NoteAppType.NOTION);
        guest.setStatus(ConfigStatus.ACTIVE);
        guest.setIsGuest(true);
        guest.setMigrationStatus("NONE");
        guest.setEncryptedApiKey("GUEST_MODE_PLACEHOLDER");
        guest.setDatabaseId(TEST_DB_ID);
        guest.setUpdatedAt(LocalDateTime.now());
        userConfigRepository.insert(guest);

        // 正式用户
        UserConfig regular = new UserConfig();
        regular.setOpenId(REGULAR_OPEN_ID);
        regular.setAppType(NoteAppType.NOTION);
        regular.setStatus(ConfigStatus.ACTIVE);
        regular.setIsGuest(false);
        regular.setMigrationStatus("NONE");
        regular.setEncryptedApiKey(AesUtil.encrypt(aesKey, TEST_NOTION_KEY));
        regular.setDatabaseId(TEST_DB_ID);
        regular.setUpdatedAt(LocalDateTime.now());
        userConfigRepository.insert(regular);

        guestToken   = jwtUtil.issue(GUEST_OPEN_ID);
        regularToken = jwtUtil.issue(REGULAR_OPEN_ID);
    }

    @AfterEach
    void tearDown() {
        for (String openId : new String[]{GUEST_OPEN_ID, REGULAR_OPEN_ID}) {
            userConfigRepository.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserConfig>()
                    .eq("open_id", openId));
        }
    }

    // ── 未认证请求返回 401 ────────────────────────────────────────────────

    @Test
    void listPages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notion/pages").param("year", "2026").param("month", "3"))
            .andExpect(status().isUnauthorized());
    }

    // ── 访客用户使用 adminToken ────────────────────────────────────────────

    @Test
    void listPages_guestUser_usesAdminToken() throws Exception {
        NotionService.QueryResult emptyResult = new NotionService.QueryResult(
            new ObjectMapper().createObjectNode().putArray("results"), null, false);
        ArgumentCaptor<String> apiKeyCaptor = ArgumentCaptor.forClass(String.class);
        when(notionService.queryDatabase(apiKeyCaptor.capture(), any(), any()))
            .thenReturn(emptyResult);

        mockMvc.perform(get("/api/notion/pages")
                .param("year", "2026").param("month", "3")
                .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isOk());

        assertEquals("admin_token", apiKeyCaptor.getValue(),
            "访客应使用 adminToken 查询 Notion");
    }

    // ── 正式用户使用解密后的 key ───────────────────────────────────────────

    @Test
    void listPages_regularUser_usesDecryptedNotionKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode results = mapper.createArrayNode();
        NotionService.QueryResult emptyResult =
            new NotionService.QueryResult(results, null, false);
        ArgumentCaptor<String> apiKeyCaptor = ArgumentCaptor.forClass(String.class);
        when(notionService.queryDatabase(apiKeyCaptor.capture(), any(), any()))
            .thenReturn(emptyResult);

        mockMvc.perform(get("/api/notion/pages")
                .param("year", "2026").param("month", "3")
                .header("Authorization", "Bearer " + regularToken))
            .andExpect(status().isOk());

        assertEquals(TEST_NOTION_KEY, apiKeyCaptor.getValue(),
            "正式用户应使用解密后的 Notion key");
    }

    // ── block-count：访客使用 adminToken ─────────────────────────────────

    @Test
    void blockCount_guestUser_usesAdminToken() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode blocks = mapper.createObjectNode();
        blocks.putArray("results");
        ArgumentCaptor<String> apiKeyCaptor = ArgumentCaptor.forClass(String.class);
        when(notionService.retrieveBlockChildren(apiKeyCaptor.capture(), eq("page-abc")))
            .thenReturn(blocks);

        mockMvc.perform(get("/api/notion/pages/page-abc/block-count")
                .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isOk());

        assertEquals("admin_token", apiKeyCaptor.getValue());
    }
}
