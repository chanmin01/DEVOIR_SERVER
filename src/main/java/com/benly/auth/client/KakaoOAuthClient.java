package com.benly.auth.client;

import com.benly.auth.client.dto.KakaoUserInfo;

public interface KakaoOAuthClient {

    KakaoUserInfo getKakaoUser(String authorizationCode);

    void unlink(String kakaoId);
}