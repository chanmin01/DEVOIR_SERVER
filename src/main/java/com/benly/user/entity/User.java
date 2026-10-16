package com.benly.user.entity;

import com.benly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true, length = 255)
    private String kakaoId;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    private User(String kakaoId, String nickname) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.termsAgreedAt = LocalDateTime.now();
    }

    public static User of(String kakaoId, String nickname) {
        return new User(kakaoId, nickname);
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void withdraw() {
        this.kakaoId = "deleted_" + this.id;
        this.nickname = "탈퇴한 사용자";
        softDelete();
    }
}
