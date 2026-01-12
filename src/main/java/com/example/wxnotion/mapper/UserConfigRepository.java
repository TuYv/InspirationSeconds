package com.example.wxnotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wxnotion.model.UserConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户配置表 Mapper。
 *
 * 继承 MyBatis-Plus BaseMapper，提供基础 CRUD 能力。
 */
@Mapper
public interface UserConfigRepository extends BaseMapper<UserConfig> {
}
