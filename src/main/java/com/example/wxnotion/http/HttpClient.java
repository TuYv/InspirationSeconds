package com.example.wxnotion.http;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP 客户端抽象接口。
 */
public interface HttpClient {
  
  /**
   * 发送 GET 请求。
   * @param url 请求地址
   * @param headers 请求头
   * @return 响应体内容
   * @throws IOException 网络异常或非 2xx 响应
   */
  String get(String url, Map<String, String> headers) throws IOException;

  /**
   * 发送 POST 请求（JSON Body）。
   * @param url 请求地址
   * @param jsonBody JSON 请求体
   * @param headers 请求头
   * @return 响应体内容
   * @throws IOException 网络异常或非 2xx 响应
   */
  String post(String url, String jsonBody, Map<String, String> headers) throws IOException;

  /**
   * 发送请求并获取完整响应（包含状态码、头信息等，用于特殊处理）。
   * 这里的返回类型可以封装一个自定义 Response 对象，但在简单场景下，
   * 我们可以先只提供 get/post 返回 String。
   * 如果需要更底层的控制（如检查 404），可以扩展此接口。
   */
   HttpResponse execute(HttpRequest request) throws IOException;

   class HttpRequest {
     public String url;
     public String method; // GET, POST
     public String body;
     public Map<String, String> headers;

     public HttpRequest(String url, String method, String body, Map<String, String> headers) {
       this.url = url;
       this.method = method;
       this.body = body;
       this.headers = headers;
     }
   }

   class HttpResponse {
     public int code;
     public String body;
     public boolean isSuccessful;

     public HttpResponse(int code, String body, boolean isSuccessful) {
       this.code = code;
       this.body = body;
       this.isSuccessful = isSuccessful;
     }
   }
}
