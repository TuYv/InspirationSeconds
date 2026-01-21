package com.example.wxnotion.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.ConversationStateRepository;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.*;
import com.example.wxnotion.util.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 交互式 Notion 配置流程服务。
 *
 * 职责：根据用户文本指令推进配置步骤，调用 Notion 验证并持久化配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigFlowService {
  private final ConversationStateRepository stateRepo;
  private final UserConfigRepository configRepo;
  private final NotionService notionService;

  @Value("${security.aesKey}")
  private String aesKey;

  /**
   * 启动或重置配置流程：设置为等待 API Key 状态。
   */
  public String startOrReset(String openId) {
    log.info("开始/重置配置流程，用户OpenID: {}", openId);
    ConversationState state = stateRepo.selectOne(new QueryWrapper<ConversationState>().eq("open_id", openId));
    if (state == null){
      state = new ConversationState();
    }
    state.setOpenId(openId);
    state.setStep(ConfigStep.WAITING_KEY);
    state.setTempApiKey(null);
    state.setUpdatedAt(LocalDateTime.now());
    if (state.getId() == null){
      stateRepo.insert(state);
    } else {
      stateRepo.updateById(state);
    }
    return "请提供Notion API Key。获取教程：https://www.soloship.top/article/2ec04d74-90b4-80c1-bd09-f765845435bc";
  }

  /**
   * 按当前步骤处理用户输入：采集 API Key 或数据库ID，并在验证后保存配置。
   *
   * 返回给微信的引导或结果文案；返回 null 表示非配置流程输入。
   */
  public String handleInput(String openId, String text) {
    ConversationState state = stateRepo.selectOne(new QueryWrapper<ConversationState>().eq("open_id", openId));
    if (state == null || state.getStep() == ConfigStep.NONE) {
      return null;
    }
    if (state.getStep() == ConfigStep.WAITING_KEY) {
      log.info("收到 API Key，用户OpenID: {}", openId);
      // 收到 API Key，进入下一步等待数据库ID
      state.setTempApiKey(text.trim());
      state.setStep(ConfigStep.WAITING_DB);
      state.setUpdatedAt(LocalDateTime.now());
      stateRepo.updateById(state);
      return "已收到API Key。请提供Notion数据库ID。获取方法：在数据库页面URL中复制ID";
    }
    if (state.getStep() == ConfigStep.WAITING_DB) {
      String apiKey = state.getTempApiKey();
      String databaseId = text.trim();
      log.info("收到数据库ID，用户OpenID: {}，开始验证...", openId);
      // 调用 Notion 验证数据库有效性与权限
      boolean ok = notionService.validate(apiKey, databaseId);
      if (ok) {
        log.info("验证成功，保存配置。用户OpenID: {}", openId);
        // 保存配置并结束流程
        UserConfig cfg = configRepo.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
        if (cfg == null) cfg = new UserConfig();
        cfg.setOpenId(openId);
        cfg.setAppType(NoteAppType.NOTION);
        cfg.setStatus(ConfigStatus.ACTIVE);
        cfg.setEncryptedApiKey(AesUtil.encrypt(aesKey, apiKey));
        cfg.setDatabaseId(databaseId);
        cfg.setUpdatedAt(LocalDateTime.now());
        if (cfg.getId() == null) configRepo.insert(cfg); else configRepo.updateById(cfg);
        state.setStep(ConfigStep.NONE);
        state.setTempApiKey(null);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepo.updateById(state);
        return "验证通过，配置已保存并启用。您可直接发送消息进行同步";
      } else {
        log.warn("验证失败，用户OpenID: {}。Key或数据库ID无效。", openId);
        // 验证失败，保持步骤等待用户重新提交
        state.setUpdatedAt(LocalDateTime.now());
        stateRepo.updateById(state);
        return "验证失败：请检查API Key或数据库ID是否有效，重新提交数据库ID或输入‘配置Notion’重新开始";
      }
    }
    return null;
  }

  /**
   * 查询当前用户的配置摘要。
   */
  public String queryConfig(String openId) {
    UserConfig c = configRepo.selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    if (c != null) {
      return "笔记软件：Notion，状态：" + c.getStatus() + "，数据库ID：" + c.getDatabaseId();
    }
    return "尚未配置。发送‘配置Notion’开始配置";
  }
}
