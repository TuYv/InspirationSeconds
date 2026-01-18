package com.example.wxnotion.model.notion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Notion 内容块 (Block) 对象。
 *
 * Notion 的页面内容由各种 Block 堆叠而成。
 * 本类封装了常用的 Block 类型（段落、列表、待办、标题等）及其数据结构。
 *
 * 对应 Notion API 中的 Block Object。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotionBlock {
    /** 对象类型，固定为 "block" */
    private String object = "block";
    
    /** Block 类型（如 paragraph, to_do, heading_3 等） */
    private String type;
    
    /** 段落内容 */
    private BlockContent paragraph;
    
    /** 无序列表项内容 */
    @JsonProperty("bulleted_list_item")
    private BlockContent bulletedListItem;
    
    /** 待办事项内容 */
    @JsonProperty("to_do")
    private ToDoContent toDo;
    
    /** 引用块内容 */
    @JsonProperty("quote")
    private BlockContent quote;
    
    /** 三级标题内容 */
    @JsonProperty("heading_3")
    private BlockContent heading3;

    // --- 静态工厂方法，用于快速构建 Block ---

    /** 创建普通段落 Block */
    public static NotionBlock paragraph(String text) {
        NotionBlock b = new NotionBlock();
        b.type = "paragraph";
        b.paragraph = new BlockContent(text);
        return b;
    }

    /** 创建无序列表项 Block */
    public static NotionBlock bulletedList(String text) {
        NotionBlock b = new NotionBlock();
        b.type = "bulleted_list_item";
        b.bulletedListItem = new BlockContent(text);
        return b;
    }

    /** 创建待办事项 Block */
    public static NotionBlock toDo(String text, boolean checked) {
        NotionBlock b = new NotionBlock();
        b.type = "to_do";
        b.toDo = new ToDoContent(text, checked);
        return b;
    }
    
    /** 创建引用块 Block */
    public static NotionBlock quote(String text) {
        NotionBlock b = new NotionBlock();
        b.type = "quote";
        b.quote = new BlockContent(text);
        return b;
    }
    
    /** 创建三级标题 Block */
    public static NotionBlock heading(String text) {
        NotionBlock b = new NotionBlock();
        b.type = "heading_3";
        b.heading3 = new BlockContent(text);
        return b;
    }

    // --- 内部内容结构类 ---

    /**
     * 通用 Block 内容结构。
     * 包含 rich_text 数组，用于存储富文本内容。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockContent {
        @JsonProperty("rich_text")
        private List<RichText> richText;

        public BlockContent(String text) {
            this.richText = Collections.singletonList(new RichText(text));
        }
    }

    /**
     * 待办事项内容结构（继承自通用结构）。
     * 额外包含 checked 状态字段。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToDoContent extends BlockContent {
        private boolean checked;
        public ToDoContent(String text, boolean checked) {
            super(text);
            this.checked = checked;
        }
    }
}
