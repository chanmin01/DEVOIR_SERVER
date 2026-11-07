package com.benly.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;
    private final Meta meta;

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, null);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(201, message, data, null);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(200, message, null, null);
    }

    public static ApiResponse<Void> error(int code, String message, String path) {
        return new ApiResponse<>(code, message, null, new Meta(path, System.currentTimeMillis()));
    }

    @Getter
    @RequiredArgsConstructor
    public static class Meta {
        private final String path;
        private final long timestamp;
    }
}
