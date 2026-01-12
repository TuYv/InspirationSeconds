package com.example.wxnotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.UserConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/configs")
public class UserConfigController {
  private final UserConfigRepository repo;
  public UserConfigController(UserConfigRepository repo) { this.repo = repo; }

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
}
