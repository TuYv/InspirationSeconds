package com.example.wxnotion.model.notion;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notion 父级对象引用。
 *
 * 用于指定新页面的归属位置。在本项目中，主要用于指向目标数据库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Parent {
    /**
     * 目标数据库的 UUID。
     * 对应 JSON 字段 `database_id`。
     */
    @JsonProperty("database_id")
    private String databaseId;
}
