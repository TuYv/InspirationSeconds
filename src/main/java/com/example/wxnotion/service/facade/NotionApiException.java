package com.example.wxnotion.service.facade;

public class NotionApiException extends RuntimeException {
  private final String code;
  private final int httpStatus;
  private final String context;

  public NotionApiException(String code, int httpStatus, String message, String context) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
    this.context = context;
  }

  public String getCode() {
    return code;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public String getContext() {
    return context;
  }
}
