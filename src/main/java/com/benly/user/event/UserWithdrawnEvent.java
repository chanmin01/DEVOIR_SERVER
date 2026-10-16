package com.benly.user.event;

import java.util.List;

public record UserWithdrawnEvent(String kakaoId, List<String> fileKeys) {}