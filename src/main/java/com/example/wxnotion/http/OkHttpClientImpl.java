package com.example.wxnotion.http;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp 的 HttpClient 实现。
 */
@Component
public class OkHttpClientImpl implements HttpClient {
  private static final Logger log = LoggerFactory.getLogger(OkHttpClientImpl.class);
  private final OkHttpClient client;

  public OkHttpClientImpl() {
    this.client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  @Override
  public String get(String url, Map<String, String> headers) throws IOException {
    HttpRequest request = new HttpRequest(url, "GET", null, headers);
    HttpResponse response = execute(request);
    if (!response.isSuccessful) {
      throw new IOException("HTTP request failed: code=" + response.code + ", body=" + response.body);
    }
    return response.body;
  }

  @Override
  public String post(String url, String jsonBody, Map<String, String> headers) throws IOException {
    HttpRequest request = new HttpRequest(url, "POST", jsonBody, headers);
    HttpResponse response = execute(request);
    if (!response.isSuccessful) {
      throw new IOException("HTTP request failed: code=" + response.code + ", body=" + response.body);
    }
    return response.body;
  }

  @Override
  public String patch(String url, String jsonBody, Map<String, String> headers) throws IOException {
    HttpRequest request = new HttpRequest(url, "PATCH", jsonBody, headers);
    HttpResponse response = execute(request);
    if (!response.isSuccessful) {
      throw new IOException("HTTP request failed: code=" + response.code + ", body=" + response.body);
    }
    return response.body;
  }

  @Override
  public HttpResponse execute(HttpRequest request) throws IOException {
    Request.Builder builder = new Request.Builder().url(request.url);
    
    if (request.headers != null) {
      request.headers.forEach(builder::header);
    }

    if ("POST".equalsIgnoreCase(request.method)) {
      RequestBody body = RequestBody.create(request.body != null ? request.body : "", MediaType.parse("application/json; charset=utf-8"));
      builder.post(body);
    } else if ("PATCH".equalsIgnoreCase(request.method)) {
      RequestBody body = RequestBody.create(request.body != null ? request.body : "", MediaType.parse("application/json; charset=utf-8"));
      builder.patch(body);
    } else {
      builder.get();
    }
    
    // Log Request
    log.info("HTTP Request: Method={}, URL={}", request.method, request.url);
    if (request.body != null && !request.body.isEmpty()) {
      log.info("HTTP Request Body: {}", request.body);
    }

    try (Response response = client.newCall(builder.build()).execute()) {
      String respBody = response.body() != null ? response.body().string() : "";
      
      // Log Response
      log.info("HTTP Response: Code={}, URL={}", response.code(), request.url);
      if (!response.isSuccessful()) {
          log.warn("HTTP Response Error Body: {}", respBody);
      } else {
          // 可选：成功响应体也打印，视内容大小决定
          log.debug("HTTP Response Body: {}", respBody);
      }
      
      return new HttpResponse(response.code(), respBody, response.isSuccessful());
    }
  }
}
