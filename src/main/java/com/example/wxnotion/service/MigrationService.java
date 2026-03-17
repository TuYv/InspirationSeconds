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
            totalMigrated = migrateAllPages(adminToken, oldDbId, newToken, newDbId);

            // 3. 迁移完成：更新配置为正式用户
            user.setIsGuest(false);
            user.setMigrationStatus("DONE");
            user.setDatabaseId(newDbId);
            user.setEncryptedApiKey(AesUtil.encrypt(aesKey, newToken));
            user.setStatus(ConfigStatus.ACTIVE);
            userConfigRepository.updateById(user);

            // 4. 归档旧库
            notionService.updateDatabase(adminToken, oldDbId, "[已迁移] GuestBox_" + openId.substring(Math.max(0, openId.length() - 6)));

            log.info("用户 {} 迁移完成，共迁移 {} 条笔记", openId, totalMigrated);
            wechatService.pushMessageToUser(openId, String.format("🎉 迁移完成！共为您搬运了 %d 条笔记。\n您现在已升级为正式用户。", totalMigrated));

        } catch (Exception e) {
            log.error("用户 {} 迁移失败", openId, e);
            user.setMigrationStatus("FAILED");
            userConfigRepository.updateById(user);
            wechatService.pushMessageToUser(openId, "数据迁移中断，请重新发送'配置Notion'重试。(您的数据未丢失)");
        }
    }

    /**
     * 迁移所有页面：遍历源数据库，逐页提取内容并写入目标数据库。
     *
     * @return 成功迁移的页面数
     * @throws Exception 迁移过程中的任何异常向上抛出，由 startMigration 统一处理
     */
    public int migrateAllPages(String srcToken, String srcDbId, String destToken, String destDbId) throws Exception {
        int totalMigrated = 0;

        String cursor = null;
        do {
            NotionService.QueryResult query = notionService.queryDatabase(srcToken, srcDbId, cursor);
            if (query == null || query.getResults() == null) break;

            for (JsonNode page : query.getResults()) {
                String pageId = page.path("id").asText();
                JsonNode props = page.path("properties");

                // 1. 提取标题 (从 Name 属性，类型为 title)
                String name = extractTextFromProperty(props.path("Name"));
                if (name.isEmpty()) {
                    name = java.time.LocalDate.now().toString();
                }

                // 2. 提取正文内容 (Blocks，支持分页)
                String blocksContent = extractPlainTextFromBlocks(srcToken, pageId);

                // 3. 提取额外属性信息 (Title 和 Description)
                String extraTitle = extractTextFromProperty(props.path("Title"));
                String description = extractTextFromProperty(props.path("Description"));

                // 4. 组合最终正文
                StringBuilder fullBody = new StringBuilder();
                if (!extraTitle.isEmpty()) {
                    fullBody.append("### ").append(extraTitle).append("\n");
                }
                if (!description.isEmpty()) {
                    fullBody.append("> ").append(description).append("\n\n");
                }
                fullBody.append(blocksContent);

                ContentUtil.NotionContent content = new ContentUtil.NotionContent();
                content.setTitle(name);
                content.setContent(fullBody.toString());

                NotionService.CreateResult result = notionService.createPage(destToken, destDbId, content);
                if (result.ok) {
                    totalMigrated++;
                } else {
                    log.warn("迁移页面失败: pageId={}, response={}", pageId, result.raw);
                }
                // 简单的速率限制 (300ms)
                Thread.sleep(300);
            }

            cursor = query.getNextCursor();
        } while (cursor != null);

        return totalMigrated;
    }
    
    private String extractTextFromProperty(JsonNode propertyNode) {
        if (propertyNode.isMissingNode()) return "";
        String type = propertyNode.path("type").asText();
        JsonNode textArray = propertyNode.path(type);
        if (!textArray.isArray()) return "";
        
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : textArray) {
            sb.append(item.path("plain_text").asText(""));
        }
        return sb.toString();
    }

    private String extractPlainTextFromBlocks(String token, String blockId) {
        StringBuilder sb = new StringBuilder();
        String cursor = null;
        do {
            JsonNode children = notionService.retrieveBlockChildren(token, blockId, cursor);
            if (children == null) break;

            JsonNode results = children.path("results");
            if (results.isArray()) {
                for (JsonNode block : results) {
                    String type = block.path("type").asText();
                    if (block.has(type) && block.path(type).has("rich_text")) {
                        for (JsonNode text : block.path(type).path("rich_text")) {
                            sb.append(text.path("plain_text").asText());
                        }
                        sb.append("\n");
                    }
                }
            }

            cursor = children.path("has_more").asBoolean(false)
                    ? children.path("next_cursor").asText(null)
                    : null;
        } while (cursor != null);
        return sb.toString();
    }
}
