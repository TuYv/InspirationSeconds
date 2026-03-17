package com.example.wxnotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.NoteAppType;
import com.example.wxnotion.model.UserConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class UserConfigController {
  private final UserConfigRepository repo;

  /**
   * 列出所有配置记录。
   */
  @GetMapping
  public List<UserConfig> list() { return repo.selectList(new QueryWrapper<>()); }

  /**
   * 根据主键获取配置。
   */
  @GetMapping("/{id}")
  public ResponseEntity<UserConfig> get(@PathVariable Long id) {
    UserConfig o = repo.selectById(id);
    return o != null ? ResponseEntity.ok(o) : ResponseEntity.notFound().build();
  }

  /**
   * 创建配置记录（供内部调试使用）。
   */
  @PostMapping
  public UserConfig create(@RequestBody @Valid UserConfig cfg) { repo.insert(cfg); return cfg; }

  /**
   * 更新配置记录（按主键覆盖）。
   */
  @PutMapping("/{id}")
  public ResponseEntity<UserConfig> update(@PathVariable Long id, @RequestBody @Valid UserConfig cfg) {
    if (repo.selectById(id) == null) return ResponseEntity.notFound().build();
    cfg.setId(id);
    repo.updateById(cfg);
    return ResponseEntity.ok(cfg);
  }

  /**
   * 删除配置记录。
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (repo.selectById(id) == null) return ResponseEntity.notFound().build();
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * 根据 openId 获取用于展示的配置视图（不包含敏感字段）。
   */
  @GetMapping("/by-openid")
  public ResponseEntity<UserConfigView> getByOpenId(@RequestParam String openId) {
    UserConfig cfg = repo.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    if (cfg == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(UserConfigView.from(cfg));
  }

  @Data
  public static class UserConfigView {
    private String openId;
    private NoteAppType appType;
    private ConfigStatus status;
    private String databaseId;
    private Boolean isGuest;
    private String migrationStatus;
    private LocalDateTime updatedAt;
    private String nickname;
    private String avatarUrl;
    private boolean dailyCardEnabled;

    public static UserConfigView from(UserConfig cfg) {
      UserConfigView view = new UserConfigView();
      view.setOpenId(cfg.getOpenId());
      view.setAppType(cfg.getAppType());
      view.setStatus(cfg.getStatus());
      view.setDatabaseId(cfg.getDatabaseId());
      view.setIsGuest(cfg.getIsGuest());
      view.setMigrationStatus(cfg.getMigrationStatus());
      view.setUpdatedAt(cfg.getUpdatedAt());
      view.setNickname(cfg.getNickname());
      view.setAvatarUrl(cfg.getAvatarUrl());
      com.example.wxnotion.model.PromptConfig pc = cfg.getPromptConfig();
      view.setDailyCardEnabled(pc == null || pc.getDailyCardEnabled() == null || pc.getDailyCardEnabled());
      return view;
    }
  }
}
