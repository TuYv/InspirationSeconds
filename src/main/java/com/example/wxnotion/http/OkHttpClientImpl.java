package com.example.wxnotion.http;

import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp 的 HttpClient 实现。
 */
@Component
public class OkHttpClientImpl implements HttpClient {

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
  public HttpResponse execute(HttpRequest request) throws IOException {
    Request.Builder builder = new Request.Builder().url(request.url);
    
    if (request.headers != null) {
      request.headers.forEach(builder::header);
    }

    if ("POST".equalsIgnoreCase(request.method)) {
      RequestBody body = RequestBody.create(request.body != null ? request.body : "", MediaType.parse("application/json; charset=utf-8"));
      builder.post(body);
    } else {
      builder.get();
    }

    try (Response response = client.newCall(builder.build()).execute()) {
      String respBody = response.body() != null ? response.body().string() : "";
      return new HttpResponse(response.code(), respBody, response.isSuccessful());
    }
  }
}
