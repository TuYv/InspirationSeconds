package com.example.wxnotion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 草稿匹配结果。
 * <pre>{"matched_draft_id": 123}</pre>
 * 若无匹配，{@code matchedDraftId} 为 null。
 */
public class TaskMatchResult {

    @JsonProperty("matched_draft_id")
    public Long matchedDraftId;
}
