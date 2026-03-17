package com.example.wxnotion.service;

import com.example.wxnotion.AbstractSpringTest;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.NoteAppType;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SyncServiceTest extends AbstractSpringTest {

    @Autowired
    private SyncService syncService;

    @Autowired
    private UserConfigRepository userConfigRepository;

    @MockBean
    private NotionService notionService;

    @Value("${security.aesKey}")
    private String aesKey;

    private static final String GUEST_OPEN_ID    = "sync_test_guest";
    private static final String REGULAR_OPEN_ID  = "sync_test_regular";
    private static final String MIGRATING_OPEN_ID = "sync_test_migrating";
    private static final String INACTIVE_OPEN_ID  = "sync_test_inactive";
    private static final String TEST_DB_ID        = "test-db-id-0001";
    private static final String TEST_PAGE_ID      = "test-page-id-0001";
    private static final String TEST_NOTION_KEY   = "secret_notion_token";

    @BeforeEach
    void setUp() throws IOException {
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

        // 迁移中用户
        UserConfig migrating = new UserConfig();
        migrating.setOpenId(MIGRATING_OPEN_ID);
        migrating.setAppType(NoteAppType.NOTION);
        migrating.setStatus(ConfigStatus.ACTIVE);
        migrating.setIsGuest(false);
        migrating.setMigrationStatus("MIGRATING");
        migrating.setEncryptedApiKey(AesUtil.encrypt(aesKey, TEST_NOTION_KEY));
        migrating.setDatabaseId(TEST_DB_ID);
        migrating.setUpdatedAt(LocalDateTime.now());
        userConfigRepository.insert(migrating);

        // 未激活用户
        UserConfig inactive = new UserConfig();
        inactive.setOpenId(INACTIVE_OPEN_ID);
        inactive.setAppType(NoteAppType.NOTION);
        inactive.setStatus(ConfigStatus.INACTIVE);
        inactive.setIsGuest(false);
        inactive.setMigrationStatus("NONE");
        inactive.setEncryptedApiKey(AesUtil.encrypt(aesKey, TEST_NOTION_KEY));
        inactive.setDatabaseId(TEST_DB_ID);
        inactive.setUpdatedAt(LocalDateTime.now());
        userConfigRepository.insert(inactive);
    }

    @AfterEach
    void tearDown() {
        for (String openId : new String[]{GUEST_OPEN_ID, REGULAR_OPEN_ID, MIGRATING_OPEN_ID, INACTIVE_OPEN_ID}) {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserConfig> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("open_id", openId);
            userConfigRepository.delete(qw);
        }
    }

    // ── 路径 1：迁移中 ─────────────────────────────────────────────────────

    @Test
    void sync_migratingUser_returnsWaitMessage() {
        String result = syncService.sync(MIGRATING_OPEN_ID, "一条笔记");
        assertTrue(result.contains("迁移中"), "迁移中用户应返回等待提示，实际：" + result);
        verifyNoInteractions(notionService);
    }

    // ── 路径 2：未激活非访客 ────────────────────────────────────────────────

    @Test
    void sync_inactiveRegularUser_returnsConfigUrl() {
        String result = syncService.sync(INACTIVE_OPEN_ID, "一条笔记");
        assertTrue(result.contains("wx.soloship.top"), "未激活用户应返回配置 URL，实际：" + result);
        verifyNoInteractions(notionService);
    }

    // ── 路径 3：访客，今日页面已存在 → 追加 ────────────────────────────────

    @Test
    void sync_guestUser_pageExists_appendsContent() throws IOException {
        when(notionService.findTodayPage(eq("admin_token"), eq(TEST_DB_ID)))
            .thenReturn(TEST_PAGE_ID);
        when(notionService.appendContent(eq("admin_token"), eq(TEST_PAGE_ID), any()))
            .thenReturn(true);

        String result = syncService.sync(GUEST_OPEN_ID, "灵感内容");
        assertTrue(result.contains("已追加"), "应返回追加成功提示，实际：" + result);
        verify(notionService).appendContent(eq("admin_token"), eq(TEST_PAGE_ID), any());
    }

    // ── 路径 4：访客，今日无页面 → 创建 ────────────────────────────────────

    @Test
    void sync_guestUser_noPageToday_createsPage() throws IOException {
        when(notionService.findTodayPage(eq("admin_token"), eq(TEST_DB_ID)))
            .thenReturn(null);
        when(notionService.createPage(eq("admin_token"), eq(TEST_DB_ID), any()))
            .thenReturn(new NotionService.CreateResult(true, "new-page-id", "{}"));

        String result = syncService.sync(GUEST_OPEN_ID, "新的一天");
        assertTrue(result.contains("今日笔记已创建"), "应返回创建成功提示，实际：" + result);
        verify(notionService).createPage(eq("admin_token"), eq(TEST_DB_ID), any());
    }

    // ── 路径 5：正式用户，今日页面存在 → 追加，使用解密后的 key ────────────

    @Test
    void sync_regularUser_pageExists_usesDecryptedKey() throws IOException {
        when(notionService.findTodayPage(eq(TEST_NOTION_KEY), eq(TEST_DB_ID)))
            .thenReturn(TEST_PAGE_ID);
        when(notionService.appendContent(eq(TEST_NOTION_KEY), eq(TEST_PAGE_ID), any()))
            .thenReturn(true);

        String result = syncService.sync(REGULAR_OPEN_ID, "工作记录");
        assertTrue(result.contains("已追加"), "正式用户应追加成功，实际：" + result);
        verify(notionService).appendContent(eq(TEST_NOTION_KEY), eq(TEST_PAGE_ID), any());
    }
}
