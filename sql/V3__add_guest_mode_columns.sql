-- V3__add_guest_mode_columns.sql

-- 为 user_config 表添加访客模式和迁移状态字段
ALTER TABLE user_config 
ADD COLUMN is_guest BOOLEAN DEFAULT FALSE COMMENT '是否为访客用户',
ADD COLUMN migration_status VARCHAR(20) DEFAULT 'NONE' COMMENT '迁移状态: NONE, MIGRATING, DONE, FAILED',
ADD COLUMN prompt_config JSON COMMENT '自定义Prompt配置';

-- 创建索引以优化查询
CREATE INDEX idx_user_config_is_guest ON user_config(is_guest);
