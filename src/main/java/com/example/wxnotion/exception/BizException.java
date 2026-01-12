package com.example.wxnotion.exception;

/**
 * 业务异常类型。
 *
 * 携带业务错误码与消息，用于统一异常反馈。
 */
public class BizException extends RuntimeException {
  private final String code;
  public BizException(String code, String message) {
    super(message);
    this.code = code;
  }
  /** 错误码 */
  public String getCode() {
    return code;
  }
}
