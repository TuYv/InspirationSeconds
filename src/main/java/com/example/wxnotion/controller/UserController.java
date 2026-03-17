package com.example.wxnotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.NoteAppType;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.service.MigrationService;
import com.example.wxnotion.util.AesUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final UserConfigRepository userConfigRepository;
  private final MigrationService migrationService;

  @Value("${security.aesKey}")
  private String aesKey;

  /**
   * 获取当前登录用户的配置视图。openId 来自 JWT。
   */
  @GetMapping("/me")
  public ResponseEntity<UserConfigController.UserConfigView> me(
      @AuthenticationPrincipal String openId) {
    UserConfig cfg = userConfigRepository.selectOne(
        new QueryWrapper<UserConfig>().eq("open_id", openId));
    if (cfg == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(UserConfigController.UserConfigView.from(cfg));
  }

  /**
   * 保存或更新当前用户的 Notion 配置。openId 来自 JWT，不可由客户端伪造。
   */
  @PutMapping("/config")
  public ResponseEntity<UserConfigController.UserConfigView> saveConfig(
      @AuthenticationPrincipal String openId,
      @RequestBody @Valid SaveConfigRequest req) {

    String encryptedToken = AesUtil.encrypt(aesKey, req.getNotionToken());

    UserConfig existing = userConfigRepository.selectOne(
        new QueryWrapper<UserConfig>().eq("open_id", openId));

    if (existing == null) {
      UserConfig cfg = new UserConfig();
      cfg.setOpenId(openId);
      cfg.setEncryptedApiKey(encryptedToken);
      cfg.setDatabaseId(req.getDatabaseId());
      cfg.setAppType(NoteAppType.NOTION);
      cfg.setStatus(ConfigStatus.ACTIVE);
      cfg.setIsGuest(false);
      cfg.setUpdatedAt(LocalDateTime.now());
      userConfigRepository.insert(cfg);
      return ResponseEntity.ok(UserConfigController.UserConfigView.from(cfg));
    } else {
      // 访客转正：触发异步数据迁移
      if (Boolean.TRUE.equals(existing.getIsGuest())) {
        migrationService.startMigration(existing, req.getNotionToken(), req.getDatabaseId());
        // 迁移过程中 startMigration 会异步更新配置，这里先返回当前状态
        existing.setMigrationStatus("MIGRATING");
        return ResponseEntity.ok(UserConfigController.UserConfigView.from(existing));
      }

      userConfigRepository.update(null, new UpdateWrapper<UserConfig>()
          .eq("open_id", openId)
          .set("encrypted_api_key", encryptedToken)
          .set("database_id", req.getDatabaseId())
          .set("status", ConfigStatus.ACTIVE)
          .set("updated_at", LocalDateTime.now()));
      existing.setEncryptedApiKey(encryptedToken);
      existing.setDatabaseId(req.getDatabaseId());
      existing.setStatus(ConfigStatus.ACTIVE);
      existing.setUpdatedAt(LocalDateTime.now());
      return ResponseEntity.ok(UserConfigController.UserConfigView.from(existing));
    }
  }

  /**
   * 更新用户偏好设置（不涉及 Notion 凭据）。
   */
  @PatchMapping("/preferences")
  public ResponseEntity<Void> updatePreferences(
      @AuthenticationPrincipal String openId,
      @RequestBody PreferencesRequest req) {

    UserConfig cfg = userConfigRepository.selectOne(
        new QueryWrapper<UserConfig>().eq("open_id", openId));
    if (cfg == null) return ResponseEntity.notFound().build();

    com.example.wxnotion.model.PromptConfig pc = cfg.getPromptConfig();
    if (pc == null) pc = new com.example.wxnotion.model.PromptConfig();
    if (req.getDailyCardEnabled() != null) {
      pc.setDailyCardEnabled(req.getDailyCardEnabled());
    }
    cfg.setPromptConfig(pc);
    cfg.setUpdatedAt(LocalDateTime.now());
    userConfigRepository.updateById(cfg);
    return ResponseEntity.ok().build();
  }

  @Data
  public static class SaveConfigRequest {
    @NotBlank
    private String notionToken;
    @NotBlank
    private String databaseId;
  }

  @Data
  public static class PreferencesRequest {
    private Boolean dailyCardEnabled;
  }
}
