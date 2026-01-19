package com.example.wxnotion.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 解析 Notion Block JSON 为纯文本 (Markdown 格式)
 * 用于将 Notion 页面内容转换为 AI 可读的格式
 */
public class BlockContentParser {

    /**
     * 解析 Notion API 返回的 Block Children 列表
     * @param root Notion API 响应的根节点 (包含 results 数组)
     * @return 转换后的 Markdown 文本
     */
    public static String parse(JsonNode root) {
        if (root == null || !root.has("results")) return "";
        
        StringBuilder sb = new StringBuilder();
        JsonNode results = root.get("results");
        
        if (results.isArray()) {
            for (JsonNode block : results) {
                String type = block.path("type").asText();
                String text = "";
                
                // 提取文本内容 (大部分 Block 的结构类似: type -> rich_text)
                if (block.has(type)) {
                    JsonNode contentNode = block.get(type);
                    if (contentNode.has("rich_text")) {
                         text = extractRichText(contentNode.get("rich_text"));
                    }
                }
                
                // 忽略空行或无内容的 Block (根据需求调整)
                if (text.trim().isEmpty() && !"divider".equals(type)) {
                    continue;
                }

                // 根据类型添加 Markdown 标记
                switch (type) {
                    case "heading_1": 
                        sb.append("# ").append(text).append("\n\n"); 
                        break;
                    case "heading_2": 
                        sb.append("## ").append(text).append("\n\n"); 
                        break;
                    case "heading_3": 
                        sb.append("### ").append(text).append("\n\n"); 
                        break;
                    case "to_do":
                        boolean checked = block.path("to_do").path("checked").asBoolean(false);
                        sb.append(checked ? "- [x] " : "- [ ] ").append(text).append("\n");
                        break;
                    case "bulleted_list_item": 
                        sb.append("- ").append(text).append("\n"); 
                        break;
                    case "numbered_list_item": 
                        sb.append("1. ").append(text).append("\n"); 
                        break;
                    case "quote": 
                        sb.append("> ").append(text).append("\n\n"); 
                        break;
                    case "divider":
                        sb.append("---\n\n");
                        break;
                    case "code":
                        sb.append("```\n").append(text).append("\n```\n\n");
                        break;
                    default: 
                        // paragraph 或其他未知类型
                        sb.append(text).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }
    
    private static String extractRichText(JsonNode richTextArray) {
        StringBuilder text = new StringBuilder();
        if (richTextArray.isArray()) {
            for (JsonNode node : richTextArray) {
                // 使用 plain_text 获取纯文本
                text.append(node.path("plain_text").asText(""));
            }
        }
        return text.toString();
    }
}
