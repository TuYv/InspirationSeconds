package com.example.wxnotion.model;

/**
 * 交互式配置流程步骤。
 * NONE：不在配置流程中
 * WAITING_KEY：等待用户提供 Notion API Key
 * WAITING_DB：等待用户提供数据库ID
 */
public enum ConfigStep {
  NONE,
  WAITING_KEY,
  WAITING_DB
}
