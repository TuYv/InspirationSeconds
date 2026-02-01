package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.mapper.UserConfigRepository;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import com.example.wxnotion.util.AesUtil;
import com.example.wxnotion.util.ContentUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationService {

    private final UserConfigRepository userConfigRepository;
    private final NotionService notionService;
    private final NotionProperties notionProperties;
    private final WechatService wechatService;

    @Value("${security.aesKey}")
    private String aesKey;

    /**
     * migration的时候需要发微信消息 发微信消息的时候可能出现配置文案 配置文案需要设定配置 设定配置的时候可能触发migration
     */

    /**
     * 启动异步迁移任务
     */
    @Async
    public void startMigration(UserConfig user, String newToken, String newDbId) {
        String openId = user.getOpenId();
        log.info("开始用户 {} 的数据迁移任务...", openId);
        
        // 1. 更新状态为 MIGRATING
        user.setMigrationStatus("MIGRATING");
        userConfigRepository.updateById(user);

        String adminToken = notionProperties.getAdminToken();
        String oldDbId = user.getDatabaseId();
        int totalMigrated = 0;
        
        try {
            // 2. 遍历旧库所有 Page (ETL)
            String cursor = null;
            do {
                NotionService.QueryResult query = notionService.queryDatabase(adminToken, oldDbId, cursor);
                if (query == null || query.getResults() == null) break;
                
                for (JsonNode page : query.getResults()) {
                    if (migrateSinglePage(adminToken, page, newToken, newDbId)) {
                        totalMigrated++;
                    }
                    // 简单的速率限制 (300ms)
                    Thread.sleep(300);
                }
                
                cursor = query.getNextCursor();
            } while (cursor != null);
            
            // 3. 迁移完成：更新配置为正式用户
            user.setIsGuest(false);
            user.setMigrationStatus("DONE");
            user.setDatabaseId(newDbId);
            user.setEncryptedApiKey(AesUtil.encrypt(aesKey, newToken));
            user.setStatus(ConfigStatus.ACTIVE);
            userConfigRepository.updateById(user);
            
            // 4. 归档旧库
            notionService.updateDatabase(adminToken, oldDbId, "[已迁移] NoteBox_" + openId.substring(Math.max(0, openId.length() - 6)));
            
            log.info("用户 {} 迁移完成，共迁移 {} 条笔记", openId, totalMigrated);
            wechatService.pushMessageToUser(openId, String.format("🎉 迁移完成！共为您搬运了 %d 条笔记。\n您现在已升级为正式用户。", totalMigrated));
            
        } catch (Exception e) {
            log.error("用户 {} 迁移失败", openId, e);
            user.setMigrationStatus("FAILED");
            userConfigRepository.updateById(user);
            wechatService.pushMessageToUser(openId, "数据迁移中断，请联系管理员处理。(您的数据未丢失)");
        }
    }

    /**
     * 迁移单个 Page
     */
    private boolean migrateSinglePage(String srcToken, JsonNode srcPage, String destToken, String destDbId) {
        try {
            String pageId = srcPage.path("id").asText();
            
            // 1. Extract Properties (Title, Date, Tags)
            // 这里简化处理：直接读取属性文本，构造 NotionContent 对象
            // 实际情况可能需要更复杂的属性映射，这里假设我们只关心 Title, Date, Content
            String title = notionService.getPageProperty(srcToken, pageId, "Name"); // 假设 Title 属性名为 Name
            // 如果 Title 为空，可能使用 Date 替代
            
            // 2. Extract Content (Blocks)
            // 我们不直接解析 Blocks，而是利用 notionService.retrieveBlockChildren 获取原始 JSON，
            // 但 createPage 接口需要 NotionBlock 对象列表，或者我们直接把 raw json 塞进去？
            // NotionService.createPage 接受 ContentUtil.NotionContent，它内部会将 String 转换为 Block。
            // 这意味着我们丢失了原有的 Block 结构（图片等）。
            // TODO: 如果要全量保留图片，createPage 需要支持直接传入 List<NotionBlock>。
            // 鉴于 NotionService 目前的封装是基于 NotionContent (String) 的，我们先尝试读取纯文本内容。
            // 如果要完美迁移，需要改造 NotionService.createPage。
            
            // 降级方案：读取所有 Block 的 plain_text 拼接成 String
            String fullContent = extractPlainTextFromBlocks(srcToken, pageId);
            
            ContentUtil.NotionContent content = new ContentUtil.NotionContent();
            content.setTitle(title);
            content.setContent(fullContent);
            // tags 暂时忽略或从 fullContent 解析
            
            // 3. Load to New DB
            NotionService.CreateResult result = notionService.createPage(destToken, destDbId, content);
            
            // 如果成功，还需要尝试迁移 Date 属性 (createPage 默认用当前时间，这里需要 override)
            // 目前 createPage 内部硬编码了 Date: start = current。
            // 如果要保留原时间，需要 updatePageProperty 修改 Date，或者改造 createPage。
            // 这里暂且接受“迁移后的笔记时间变成当前时间”，或者在正文中注明原时间。
            
            return result.ok;
            
        } catch (Exception e) {
            log.warn("迁移页面失败: {}", srcPage.path("id").asText(), e);
            return false;
        }
    }
    
    private String extractPlainTextFromBlocks(String token, String blockId) {
        StringBuilder sb = new StringBuilder();
        JsonNode children = notionService.retrieveBlockChildren(token, blockId);
        if (children != null && children.path("results").isArray()) {
            for (JsonNode block : children.path("results")) {
                String type = block.path("type").asText();
                if (block.has(type) && block.path(type).has("rich_text")) {
                    for (JsonNode text : block.path(type).path("rich_text")) {
                        sb.append(text.path("plain_text").asText());
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }
}
