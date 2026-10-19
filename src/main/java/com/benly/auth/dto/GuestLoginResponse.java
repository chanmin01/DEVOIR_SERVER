package com.benly.auth.dto;

public record GuestLoginResponse(
        Long userId,
        String accessToken,
        String nickname
) {
    public static GuestLoginResponse of(Long userId, String accessToken, String nickname) {
        return new GuestLoginResponse(userId, accessToken, nickname);
    }
}