package com.example.wxnotion.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BlockContentParserTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode makeBlock(String type, String text) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", type);
        ObjectNode typeNode = block.putObject(type);
        ArrayNode richText = typeNode.putArray("rich_text");
        ObjectNode rt = richText.addObject();
        rt.put("plain_text", text);
        return block;
    }

    private ObjectNode wrapResults(ObjectNode... blocks) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode results = root.putArray("results");
        for (ObjectNode b : blocks) results.add(b);
        return root;
    }

    @Test
    void paragraph() {
        ObjectNode root = wrapResults(makeBlock("paragraph", "Hello world"));
        String md = BlockContentParser.toMarkdown(root);
        assertEquals("Hello world", md);
    }

    @Test
    void heading2() {
        ObjectNode root = wrapResults(makeBlock("heading_2", "标题内容"));
        String md = BlockContentParser.toMarkdown(root);
        assertTrue(md.startsWith("## 标题内容"));
    }

    @Test
    void heading1And3() {
        ObjectNode root = wrapResults(
                makeBlock("heading_1", "H1"),
                makeBlock("heading_3", "H3")
        );
        String md = BlockContentParser.toMarkdown(root);
        assertTrue(md.contains("# H1"));
        assertTrue(md.contains("### H3"));
    }

    @Test
    void bulletedList() {
        ObjectNode root = wrapResults(makeBlock("bulleted_list_item", "item"));
        String md = BlockContentParser.toMarkdown(root);
        assertTrue(md.contains("- item"));
    }

    @Test
    void numberedList() {
        ObjectNode root = wrapResults(makeBlock("numbered_list_item", "step"));
        String md = BlockContentParser.toMarkdown(root);
        assertTrue(md.contains("1. step"));
    }

    @Test
    void toDoUnchecked() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "to_do");
        ObjectNode todo = block.putObject("to_do");
        todo.put("checked", false);
        ArrayNode rt = todo.putArray("rich_text");
        rt.addObject().put("plain_text", "task");
        String md = BlockContentParser.toMarkdown(wrapResults(block));
        assertTrue(md.contains("- [ ] task"));
    }

    @Test
    void toDoChecked() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "to_do");
        ObjectNode todo = block.putObject("to_do");
        todo.put("checked", true);
        ArrayNode rt = todo.putArray("rich_text");
        rt.addObject().put("plain_text", "done");
        String md = BlockContentParser.toMarkdown(wrapResults(block));
        assertTrue(md.contains("- [x] done"));
    }

    @Test
    void codeBlock() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "code");
        ObjectNode code = block.putObject("code");
        code.put("language", "java");
        ArrayNode rt = code.putArray("rich_text");
        rt.addObject().put("plain_text", "int x = 1;");
        String md = BlockContentParser.toMarkdown(wrapResults(block));
        assertTrue(md.contains("```java"));
        assertTrue(md.contains("int x = 1;"));
    }

    @Test
    void imageBlock() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "image");
        ObjectNode image = block.putObject("image");
        image.putObject("file").put("url", "https://example.com/img.png");
        String md = BlockContentParser.toMarkdown(wrapResults(block));
        assertTrue(md.contains("![](https://example.com/img.png)"));
    }

    @Test
    void unknownTypeWithRichText() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "synced_block");
        ObjectNode node = block.putObject("synced_block");
        node.putArray("rich_text").addObject().put("plain_text", "fallback text");
        String md = BlockContentParser.toMarkdown(wrapResults(block));
        assertTrue(md.contains("fallback text"));
    }

    @Test
    void unknownTypeWithNoRichText_doesNotThrow() {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "table");
        block.putObject("table");
        // should not throw, should just skip
        assertDoesNotThrow(() -> BlockContentParser.toMarkdown(wrapResults(block)));
    }

    @Test
    void nullRootReturnsEmpty() {
        assertEquals("", BlockContentParser.toMarkdown(null));
    }
}
