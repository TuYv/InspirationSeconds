package com.example.wxnotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wxnotion.model.TokenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {

    /**
     * 原子累加当日用量：存在则累加，不存在则插入。
     */
    @Update("""
            INSERT INTO token_usage (open_id, usage_date, prompt_tokens, completion_tokens, total_tokens, call_count)
            VALUES (#{openId}, #{date}, #{promptTokens}, #{completionTokens}, #{totalTokens}, 1)
            ON DUPLICATE KEY UPDATE
              prompt_tokens     = prompt_tokens     + VALUES(prompt_tokens),
              completion_tokens = completion_tokens + VALUES(completion_tokens),
              total_tokens      = total_tokens      + VALUES(total_tokens),
              call_count        = call_count        + 1
            """)
    void upsertUsage(@Param("openId") String openId,
                     @Param("date") java.time.LocalDate date,
                     @Param("promptTokens") int promptTokens,
                     @Param("completionTokens") int completionTokens,
                     @Param("totalTokens") int totalTokens);
}
