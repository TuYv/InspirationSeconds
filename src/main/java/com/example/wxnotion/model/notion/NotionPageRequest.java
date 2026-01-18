package com.example.wxnotion.model.notion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Notion 创建页面请求体 (DTO)。
 *
 * 对应 Notion API `POST /v1/pages` 的 JSON 结构。
 * 用于在指定数据库中创建一个新页面，包含属性（如标题）和正文内容（Block 列表）。
 *
 * 结构示例：
 * {
 *   "parent": { "database_id": "..." },
 *   "properties": { "Name": { "title": [...] } },
 *   "children": [ ... ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotionPageRequest {
    /**
     * 父级对象信息，通常指向一个 Database。
     */
    private Parent parent;

    /**
     * 页面属性集合。
     * Key 为数据库的属性名（如 "Name", "Tags"），Value 为对应的属性值对象。
     * 必须包含数据库的主标题属性（Title）。
     */
    private Map<String, Object> properties;

    /**
     * 页面正文内容，由一系列 Block 组成。
     * 可包含段落、列表、待办事项等。
     */
    private List<NotionBlock> children;

    public NotionPageRequest(String databaseId, Map<String, Object> properties, List<NotionBlock> children) {
        this.parent = new Parent(databaseId);
        this.properties = properties;
        this.children = children;
    }
}
