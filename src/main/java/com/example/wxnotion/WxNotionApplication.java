package com.example.wxnotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 应用入口。
 *
 * - 启用 Spring Boot 自动配置
 * - 扫描 MyBatis-Plus Mapper 接口所在包
 */
@SpringBootApplication
@MapperScan("com.example.wxnotion.mapper")
public class WxNotionApplication {
  /**
   * 启动方法，运行内置容器并加载所有配置与 Bean。
   */
  public static void main(String[] args) {
    SpringApplication.run(WxNotionApplication.class, args);
  }
}
