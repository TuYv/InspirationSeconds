package com.example.wxnotion.model.notion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notion 富文本对象 (Rich Text Object)。
 *
 * 用于在 Block 或属性中表示文本内容。
 * Notion 的文本不仅仅是字符串，而是包含样式、链接等信息的对象数组。
 * 本项目目前仅使用最基础的纯文本类型。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RichText {
    /** 文本类型，固定为 "text" */
    private String type = "text";
    
    /** 文本内容对象 */
    private Text text;

    public RichText(String content) {
        this.text = new Text(content);
    }

    /**
     * 内部文本结构。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Text {
        /** 实际的文本字符串 */
        private String content;
    }
}
