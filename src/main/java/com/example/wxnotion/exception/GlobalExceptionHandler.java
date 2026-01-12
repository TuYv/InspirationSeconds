package com.example.wxnotion.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
  /**
   * 业务异常统一处理：返回 400 与错误码+信息。
   */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", e.getCode());
    body.put("message", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * 其他异常处理：返回 500 与异常消息。
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", "INTERNAL_ERROR");
    body.put("message", e.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
