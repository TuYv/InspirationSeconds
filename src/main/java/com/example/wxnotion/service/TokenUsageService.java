package com.example.wxnotion.service;

import com.example.wxnotion.mapper.TokenUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Token 用量记录服务。按天聚合，原子累加。
 * 仅在用户使用自有 AI Key 时调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageMapper tokenUsageMapper;

    /**
     * 记录一次 AI 调用的 token 用量。
     */
    public void record(String openId, int promptTokens, int completionTokens, int totalTokens) {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            tokenUsageMapper.upsertUsage(openId, today, promptTokens, completionTokens, totalTokens);
        } catch (Exception e) {
            log.warn("记录 token 用量失败，用户: {}: {}", openId, e.getMessage());
        }
    }
}
