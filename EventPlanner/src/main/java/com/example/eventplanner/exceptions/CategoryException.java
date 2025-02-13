package com.example.eventplanner.exceptions;

import lombok.Getter;

public class CategoryException extends RuntimeException {
  public enum ErrorType {
    CATEGORY_NOT_FOUND,
    EVENT_NOT_FOUND,
    MERCHANDISE_NOT_FOUND,
    CATEGORY_IN_USE
  }

  @Getter
  private ErrorType errorType;

  public CategoryException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }
}
