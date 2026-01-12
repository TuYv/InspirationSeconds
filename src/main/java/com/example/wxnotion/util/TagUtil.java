package com.example.wxnotion.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本标签解析工具。
 *
 * 规则：匹配以 `#` 开头的连续字符（字母/数字/下划线/连字符），提取为标签；
 * 正文为去除标签后的剩余文本并做空白规整。
 */
public class TagUtil {
  private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_-]+)");

  public static class Parsed {
    private final String body;
    private final List<String> tags;
    public Parsed(String body, List<String> tags) { this.body = body; this.tags = tags; }
    /** 正文文本 */
    public String getBody() { return body; }
    /** 提取的标签列表 */
    public List<String> getTags() { return tags; }
  }

  /**
   * 解析输入文本，返回正文与标签集合。
   */
  public static Parsed parse(String text) {
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
    // 规整空白：合并连续空格并去首尾空白
    String body = sb.toString().replaceAll("\\s+", " ").trim();
    return new Parsed(body, tags);
  }
}
