package com.example.wxnotion.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wxnotion.model.ConfigStatus;
import com.example.wxnotion.model.UserConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户配置表 Mapper。
 *
 * 继承 MyBatis-Plus BaseMapper，提供基础 CRUD 能力。
 */
@Mapper
public interface UserConfigRepository extends BaseMapper<UserConfig> {

    default UserConfig selectByOpenId(String openId) {
        return selectOne(new QueryWrapper<UserConfig>().eq("open_id", openId));
    }

    default List<UserConfig> selectActiveUsers() {
        return selectList(new QueryWrapper<UserConfig>().eq("status", ConfigStatus.ACTIVE));
    }
}
