package com.example.wxnotion.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class TagUtilTest {
  @Test
  void parseExtractsTagsAndBody() {
    TagUtil.Parsed p = TagUtil.parse("今天加班 #工作 #日报");
    assertEquals("今天加班", p.getBody());
    assertEquals(Arrays.asList("工作", "日报"), p.getTags());
  }

  @Test
  void parseNoTags() {
    TagUtil.Parsed p = TagUtil.parse("纯文本内容");
    assertEquals("纯文本内容", p.getBody());
    assertTrue(p.getTags().isEmpty());
  }
}

