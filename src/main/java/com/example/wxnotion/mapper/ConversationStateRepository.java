package com.example.wxnotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wxnotion.model.ConversationState;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话临时状态表 Mapper。
 */
@Mapper
public interface ConversationStateRepository extends BaseMapper<ConversationState> {
}
