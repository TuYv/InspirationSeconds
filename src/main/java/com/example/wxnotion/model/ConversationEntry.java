package com.example.wxnotion.model;

import lombok.Data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 任务对话历史中的单条记录。
 * 替代原先的 {@code Map<String, String>} 写法。
 */
@Data
public class ConversationEntry {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    /** "user" 或 "assistant" */
    private String role;

    private String content;

    /** 格式：MM-dd HH:mm（Asia/Shanghai） */
    private String timestamp;

    /**
     * 工厂方法：创建一条带当前时间戳的对话条目。
     */
    public static ConversationEntry of(String role, String content) {
        ConversationEntry e = new ConversationEntry();
        e.role = role;
        e.content = content;
        e.timestamp = FMT.format(Instant.now());
        return e;
    }
}
