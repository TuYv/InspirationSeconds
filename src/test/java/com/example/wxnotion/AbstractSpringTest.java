package com.example.wxnotion;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 所有 @SpringBootTest 测试类的基类。
 * 补充 WX_ENCODING_AES_KEY 的测试占位值，避免 Spring 上下文启动失败。
 */
@SpringBootTest
@TestPropertySource(properties = {
    "wx.encodingAesKey=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
})
public abstract class AbstractSpringTest {
}
