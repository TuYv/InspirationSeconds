package com.example.wxnotion.service;

import com.example.wxnotion.config.NotionProperties;
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

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    NotionProperties props = new NotionProperties();
    props.setVersion("2022-06-28");
    notion = new NotionService(props) {
      @Override
      public boolean validate(String apiKey, String databaseId) {
        return true;
      }
      @Override
      protected String findTitleProperty(String apiKey, String databaseId) {
        return "Name";
      }
      @Override
      public CreateResult createPage(String apiKey, String databaseId, String title, java.util.List<String> tags) throws IOException {
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
    NotionService.CreateResult res = notion.createPage("key", "db", "正文", Arrays.asList("工作", "日报"));
    assertTrue(res.ok);
  }
}

