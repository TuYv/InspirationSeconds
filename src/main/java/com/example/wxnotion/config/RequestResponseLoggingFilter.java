package com.example.wxnotion.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 全局请求响应日志过滤器。
 * 记录所有进入系统的 HTTP 请求详情及响应结果。
 */
@Slf4j
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 包装 Request 和 Response 以便重复读取 Body
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录请求日志
            String requestBody = new String(requestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            // 避免打印过长的请求体（如文件上传），截取前1000字符
            if (requestBody.length() > 1000) {
                requestBody = requestBody.substring(0, 1000) + "...(truncated)";
            }
            // 替换换行符，保持日志整洁
            requestBody = requestBody.replaceAll("\\r|\\n", "");

            log.info("[Inbound] {} {} | IP={} | Body={}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    request.getRemoteAddr(),
                    requestBody.isEmpty() ? "-" : requestBody);

            // 记录响应日志
            String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (responseBody.length() > 1000) {
                responseBody = responseBody.substring(0, 1000) + "...(truncated)";
            }
            
            log.info("[Outbound] {} {} | Status={} | Time={}ms | Body={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    responseBody.isEmpty() ? "-" : responseBody);

            // 必须将缓存的响应内容写回原 Response，否则客户端收不到数据
            responseWrapper.copyBodyToResponse();
        }
    }
}
