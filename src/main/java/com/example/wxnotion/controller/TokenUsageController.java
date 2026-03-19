package com.example.wxnotion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.wxnotion.mapper.TokenUsageMapper;
import com.example.wxnotion.model.TokenUsage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class TokenUsageController {

    private final TokenUsageMapper tokenUsageMapper;

    /**
     * 返回当前用户所有日期的 token 用量，按 usage_date 倒序。
     */
    @GetMapping("/token-usage")
    public ResponseEntity<List<TokenUsageView>> getTokenUsage(
            @AuthenticationPrincipal String openId) {

        List<TokenUsage> records = tokenUsageMapper.selectList(
                new QueryWrapper<TokenUsage>()
                        .eq("open_id", openId)
                        .orderByDesc("usage_date"));

        List<TokenUsageView> views = records.stream()
                .map(TokenUsageView::from)
                .toList();

        return ResponseEntity.ok(views);
    }

    @Data
    public static class TokenUsageView {
        private LocalDate usageDate;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private int callCount;

        public static TokenUsageView from(TokenUsage r) {
            TokenUsageView v = new TokenUsageView();
            v.setUsageDate(r.getUsageDate());
            v.setPromptTokens(r.getPromptTokens());
            v.setCompletionTokens(r.getCompletionTokens());
            v.setTotalTokens(r.getTotalTokens());
            v.setCallCount(r.getCallCount());
            return v;
        }
    }
}
