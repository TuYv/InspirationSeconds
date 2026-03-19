package com.example.wxnotion.service;

import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.TaskDetectionResult;
import com.example.wxnotion.model.TaskDraft;
import com.example.wxnotion.model.UserConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandlerWxPortalService implements ApplicationContextAware {

  private static final String WEB_CONFIG_URL = "https://wx.soloship.top";

  private final SyncService syncService;
  private final WxMpService wxMpService;
  private final WechatService wechatService;
  private final UserConfigRepository userConfigRepository;
  private final TaskDetectionService taskDetectionService;
  private final TaskDraftService taskDraftService;
  private final TaskNotionService taskNotionService;
  private final TaskLifecycleService taskLifecycleService;
  private final TaskReminderService taskReminderService;
  private HandlerWxPortalService proxyInstance;
  private ApplicationContext applicationContext;

  private HandlerWxPortalService getThis() {
    if (proxyInstance == null) {
      proxyInstance = applicationContext.getBean(HandlerWxPortalService.class);
    }
    return proxyInstance;
  }

  /**
   * 微信消息处理入口。
   * 事件消息（关注等）直接处理，其他消息异步同步到 Notion。
   */
  public void handle(WxMpXmlMessage in) {
    String openId = in.getFromUser();
    String msgType = in.getMsgType();
    String content = StringUtils.defaultString(in.getContent(), "").trim();

    log.info("接收到微信消息，开始异步处理。用户: {}, 消息类型: {}", openId, msgType);

    this.getThis().processMessageAsync(in, openId, msgType, content);
  }

  /**
   * 异步处理消息
   */
  @Async
  public void processMessageAsync(WxMpXmlMessage in, String openId, String msgType, String content) {
    log.info("异步处理消息，用户: {}, 消息类型: {}", openId, msgType);

    try {
      String reply = switch (msgType) {
          case "event" -> processEvent(in, openId);
          case "text" -> processTextMessage(openId, content);
          case "image" -> processImageMessage(in, openId);
          default -> "暂不支持处理此类消息";
      };

      if (reply != null) {
        wechatService.pushMessageToUser(openId, reply);
      }
    } catch (Exception e) {
      log.error("异步处理消息失败，用户: {}", openId, e);
      wechatService.pushMessageToUser(openId, "处理您的消息时出现错误，请稍后重试");
    }
  }

  /**
   * 文字消息路由：
   * 1. 有 PENDING 草稿 → 尝试匹配为草稿回复
   * 2. 无匹配草稿 → AI 检测是否为任务
   * 3. 非任务 → 普通笔记 → SyncService
   */
  private String processTextMessage(String openId, String content) {
    // 查询当前用户
    UserConfig userConfig = userConfigRepository.selectByOpenId(openId);

    // ── 分支1：有 PENDING 草稿，先尝试匹配 ──
    List<TaskDraft> pendingDrafts = taskDraftService.findPendingDrafts(openId);
    if (!pendingDrafts.isEmpty()) {
      TaskDraft matched = taskDraftService.matchReplyToDraft(userConfig, content);
      if (matched != null) {
        return handleDraftReply(userConfig, matched, content);
      }
      // 消息无法匹配草稿，继续往下走任务检测
    }

    // ── 分支2：AI 检测是否为新任务 ──
    String activeTaskSummary = (userConfig != null)
            ? taskNotionService.getActiveTaskSummary(userConfig) : "[]";
    TaskDetectionResult detection = taskDetectionService.detectTask(userConfig, content, activeTaskSummary);

    if (detection.isTask()) {
      return handleNewTask(openId, userConfig, content, detection);
    }

    // ── 分支3：关联到已有任务 → 进度更新 ──
    if (detection.getRelatedTaskId() != null && userConfig != null) {
      return taskLifecycleService.updateProgress(
              userConfig, detection.getRelatedTaskId(),
              detection.getExtracted() != null ? detection.getExtracted().getName() : "任务",
              content);
    }

    // ── 分支4：检测终结意图 ──
    if (userConfig != null) {
      com.example.wxnotion.model.TaskTerminationResult intent =
              taskLifecycleService.detectTerminationIntent(userConfig, content, activeTaskSummary);
      if (intent.isTermination && intent.taskPageId != null) {
        return taskLifecycleService.executeTermination(
                userConfig, intent.taskPageId, intent.taskName, intent.status);
      }
    }

    // ── 分支5：普通笔记 ──
    return syncService.sync(openId, content);
  }

  /**
   * 处理草稿回复：填字段，若完整则创建 Notion 任务，否则继续追问。
   */
  private String handleDraftReply(UserConfig userConfig, TaskDraft draft, String userMessage) {
    List<String> missing = draft.getDraftJson().getMissingFields();

    if (!missing.isEmpty()) {
      String filledField = missing.get(0);
      // applyReplyToDraft 会在 draft 对象上原地更新 missing_fields
      taskDraftService.applyReplyToDraft(draft, filledField, userMessage, userMessage, null);
    }

    if (taskDraftService.isDraftComplete(draft)) {
      return finalizeDraft(userConfig, draft);
    }

    // 还有待收集字段，生成追问
    String nextQuestion = taskDraftService.generateClarificationQuestion(userConfig, draft);
    taskDraftService.appendToConversation(draft, "assistant", nextQuestion);
    return nextQuestion;
  }

  /**
   * 草稿信息完整，创建 Notion 任务页面，清理草稿。
   */
  private String finalizeDraft(UserConfig userConfig, TaskDraft draft) {
    try {
      String pageId = taskNotionService.createTaskPage(userConfig, draft);
      taskNotionService.appendConversationHistory(userConfig, pageId, draft.getConversationLog());

      // 生成并注册 Quartz 提醒
      String taskName = draft.getDraftJson().getName() != null ? draft.getDraftJson().getName() : "任务";
      String taskType = draft.getDraftJson().getTaskType() != null ? draft.getDraftJson().getTaskType() : "";
      String cycle    = draft.getDraftJson().getCycle();
      String cron = taskReminderService.generateCronForTask(userConfig, taskName, taskType, cycle);
      taskReminderService.scheduleReminder(userConfig, pageId, taskName, cron);
      taskNotionService.writeCronExpr(userConfig, pageId, cron);

      taskDraftService.deleteDraft(draft.getId());
      return "✅ 任务「" + taskName + "」已创建！我会在合适的时候提醒你。";
    } catch (Exception e) {
      log.error("创建任务失败，用户: {}", userConfig.getOpenId(), e);
      return "任务信息收集完毕，但写入 Notion 时出错，请稍后重试。";
    }
  }

  /**
   * 处理新任务：信息完整则直接创建，否则创建草稿并追问。
   */
  private String handleNewTask(String openId, UserConfig userConfig, String content, TaskDetectionResult detection) {
    if (userConfig == null) {
      // 用户未初始化，降级为笔记同步
      return syncService.sync(openId, content);
    }

    TaskDraft draft = taskDraftService.createDraft(openId, content, detection);

    if (taskDraftService.isDraftComplete(draft)) {
      return finalizeDraft(userConfig, draft);
    }

    // 需要追问
    String question = taskDraftService.generateClarificationQuestion(userConfig, draft);
    taskDraftService.appendToConversation(draft, "assistant", question);
    return question;
  }

/**
   * 处理微信事件（关注、取消关注等）
   */
  private String processEvent(WxMpXmlMessage in, String openId) {
    String event = StringUtils.defaultString(in.getEvent(), "");
    return switch (event.toLowerCase()) {
      case "subscribe" -> {
        log.info("新用户关注: {}", openId);
        yield "欢迎关注灵感妙记！\n\n"
            + "在这里发送的文字会自动同步到你的 Notion 笔记本。\n\n"
            + "首次使用请先完成配置：\n"
            + WEB_CONFIG_URL + "\n\n"
            + "配置完成后，直接发消息就能记录啦。";
      }
      case "unsubscribe" -> {
        log.info("用户取消关注: {}", openId);
        yield null;
      }
      default -> {
        log.info("未处理的事件类型: {}, 用户: {}", event, openId);
        yield null;
      }
    };
  }

  /**
   * 处理图片消息
   */
  private String processImageMessage(WxMpXmlMessage in, String openId)   {
    // 获取图片的 MediaId 和 PicUrl
    String mediaId = in.getMediaId();
    String picUrl = in.getPicUrl();

    log.info("收到图片消息，MediaId: {}, PicUrl: {}, 用户: {}", mediaId, picUrl, openId);

    // 目前暂时回复提示信息，告知用户图片已收到但暂不处理
    return "已收到您的图片，但目前暂不支持图片同步到Notion。请使用文字消息同步笔记。";
  }

  /**
   * 推送图片给用户（客服消息）
   */
  public void pushImageToUser(String openId, File imageFile) {
      try {
          // 1. 上传图片到微信服务器 (获得 media_id)
          // "image" 是微信规定的媒体类型
          WxMediaUploadResult uploadResult = wxMpService.getMaterialService().mediaUpload(WxConsts.MediaFileType.IMAGE, imageFile);
          String mediaId = uploadResult.getMediaId();

          // 2. 构建图片客服消息
          WxMpKefuMessage kefuMsg = WxMpKefuMessage.IMAGE()
                  .toUser(openId)
                  .mediaId(mediaId)
                  .build();

          // 3. 发送
          wxMpService.getKefuService().sendKefuMessage(kefuMsg);
          
          log.info("图片已推送给用户: {}, MediaId: {}", openId, mediaId);

      } catch (Exception e) {
          log.error("推送图片给用户失败: {}", e.getMessage(), e);
          wechatService.pushMessageToUser(openId, "日签图片生成失败，请稍后重试");
      }
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}
