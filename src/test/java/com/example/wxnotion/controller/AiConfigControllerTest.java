package com.example.wxnotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.MigrationService;
import com.example.wxnotion.util.AesUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AiConfigControllerTest {

    UserConfigRepository repo;
    UserController controller;
    private static final String AES_KEY = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void setUp() {
        repo = mock(UserConfigRepository.class);
        MigrationService migration = mock(MigrationService.class);
        controller = new UserController(repo, migration);
        ReflectionTestUtils.setField(controller, "aesKey", AES_KEY);
    }

    @Test
    void saveAiConfig_encryptsKey() {
        UserConfig existing = new UserConfig();
        existing.setOpenId("u1");
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        UserController.AiConfigRequest req = new UserController.AiConfigRequest();
        req.setAiBaseUrl("https://api.test/v1");
        req.setAiApiKey("sk-real-key");
        req.setAiModel("test-model");

        controller.updateAiConfig("u1", req);

        verify(repo).updateById((UserConfig) argThat(o -> {
            UserConfig cfg = (UserConfig) o;
            assertNotNull(cfg.getAiApiKey());
            assertNotEquals("sk-real-key", cfg.getAiApiKey()); // encrypted
            assertEquals("https://api.test/v1", cfg.getAiBaseUrl());
            assertEquals("test-model", cfg.getAiModel());
            // decrypted should match original
            assertEquals("sk-real-key", AesUtil.decrypt(AES_KEY, cfg.getAiApiKey()));
            return true;
        }));
    }

    @Test
    void saveAiConfig_emptyKey_clearsConfig() {
        UserConfig existing = new UserConfig();
        existing.setOpenId("u1");
        existing.setAiApiKey("some-encrypted-value");
        when(repo.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        UserController.AiConfigRequest req = new UserController.AiConfigRequest();
        req.setAiApiKey("");

        controller.updateAiConfig("u1", req);

        verify(repo).updateById((UserConfig) argThat(o -> {
            UserConfig cfg = (UserConfig) o;
            assertNull(cfg.getAiApiKey());
            return true;
        }));
    }

    @Test
    void maskedKey_returnsPlaceholderNotNull() {
        UserConfig cfg = new UserConfig();
        cfg.setOpenId("u1");
        cfg.setAiApiKey("some-encrypted-value");

        UserConfigController.UserConfigView view = UserConfigController.UserConfigView.from(cfg);
        assertNotNull(view.getAiApiKeyMasked());
        assertFalse(view.getAiApiKeyMasked().contains("encrypted"));
    }
}
