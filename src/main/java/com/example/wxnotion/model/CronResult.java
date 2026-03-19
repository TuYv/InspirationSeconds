package com.example.wxnotion.model;

/**
 * AI cron 表达式生成结果。
 * <pre>{"cron": "0 0 20 * * ?"}</pre>
 * 调用方需用 {@code CronExpression.isValidExpression(cron)} 校验合法性，非法时降级到默认值。
 */
public class CronResult {

    public String cron;
}
