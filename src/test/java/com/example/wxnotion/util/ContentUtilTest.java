package com.example.wxnotion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContentUtilTest {

    @Test
    void singleLine_noTags_becomesTitle() {
        ContentUtil.NotionContent c = ContentUtil.trans("今天天气很好");
        assertEquals("今天天气很好", c.getTitle());
        assertEquals("", c.getContent());
        assertTrue(c.getTags().isEmpty());
    }

    @Test
    void multiLine_firstLineIsTitle_restIsContent() {
        ContentUtil.NotionContent c = ContentUtil.trans("标题行\n正文第一行\n正文第二行");
        assertEquals("标题行", c.getTitle());
        assertEquals("正文第一行\n正文第二行", c.getContent());
    }

    @Test
    void tags_extractedAndRemovedFromBody() {
        ContentUtil.NotionContent c = ContentUtil.trans("今天学习了 Spring #技术 #学习");
        assertEquals(2, c.getTags().size());
        assertTrue(c.getTags().contains("技术"));
        assertTrue(c.getTags().contains("学习"));
        assertFalse(c.getTitle().contains("#"));
    }

    @Test
    void multiLineWithTags_titleAndTagsBothCorrect() {
        ContentUtil.NotionContent c = ContentUtil.trans("完成了需求评审 #工作\n详细记录了各项细节");
        assertEquals("完成了需求评审", c.getTitle());
        assertTrue(c.getTags().contains("工作"));
        assertEquals("详细记录了各项细节", c.getContent());
    }
}
