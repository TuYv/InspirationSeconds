package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
import com.example.wxnotion.http.OkHttpClientImpl;
import com.example.wxnotion.util.ContentUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class NotionServiceTest {
  MockWebServer server;
  NotionService notion;
  OkHttpClientImpl okHttpClient;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    NotionProperties props = new NotionProperties();
    props.setVersion("2022-06-28");
    notion = new NotionService(okHttpClient, props) {
      @Override
      public boolean validate(String apiKey, String databaseId) {
        return true;
      }
      @Override
      protected String findTitleProperty(String apiKey, String databaseId) {
        return "Name";
      }
      @Override
      public CreateResult createPage(String apiKey, String databaseId, ContentUtil.NotionContent content) throws IOException {
        return new CreateResult(true, "pageId", "{}");
      }
    };
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void buildCreatePage() throws IOException {
    ContentUtil.NotionContent notionContent = new ContentUtil.NotionContent("正文", "内容", Arrays.asList("工作", "日报"));
    NotionService.CreateResult res = notion.createPage("key", "db", notionContent);
    assertTrue(res.ok);
  }
}

