package com.example.wxnotion.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentUtil {
    private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_-]+)");
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotionContent {
        private String title;
        private String content;

        private List<String> tags;
    }

    public static NotionContent trans(String text) {

        List<String> tags = new ArrayList<>();
        Matcher m = TAG.matcher(text);
        StringBuffer sb = new StringBuffer();
        int last = 0;
        while (m.find()) {
            // 记录标签内容（不包含开头的 #）
            tags.add(m.group(1));
            // 在正文中跳过标签片段，只拼接标签之前的内容
            sb.append(text, last, m.start());
            last = m.end();
        }
        sb.append(text.substring(last));
        // 规整空白：去首尾空白，保留换行
        String body = sb.toString().trim();
        String title = "";
        String pageContent = "";

        // 智能拆分：以第一行作为标题，剩余部分作为正文
        int firstNewLine = body.indexOf('\n');
        if (firstNewLine > 0) {
            title = body.substring(0, firstNewLine).trim();
            pageContent = body.substring(firstNewLine + 1).trim();
        } else {
            title = body;
        }
        return new NotionContent(title, pageContent, tags);
    }
}
