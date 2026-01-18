package com.example.wxnotion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author 瑞克
 * @date 2026/1/18 11:39
 * @description
 */
@Getter
@AllArgsConstructor
public enum MarkDownTextTypeEnum {
    PARAGRAPH("paragraph", "段落"),
    BULLETED_LIST_ITEM("bulleted_list_item", "无序列表"),
    TO_DO("to_do", "待办"),
    QUOTE("quote", "引用"),
    HEADING_3("heading_3", "三级标题");


    ;
    private final String type;
    private final String desc;
}
