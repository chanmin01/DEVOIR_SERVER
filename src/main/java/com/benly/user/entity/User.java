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

    private static final String GUEST_NICKNAME = "게스트";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 게스트는 카카오 ID가 없으므로 nullable.
    // MySQL UNIQUE 컬럼은 NULL 중복을 허용하므로 unique 제약은 유지한다.
    @Column(name = "kakao_id", unique = true, length = 255)
    private String kakaoId;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    // 게스트는 약관 동의 전이므로 nullable.
    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 10)
    private UserType userType;

    private User(String kakaoId, String nickname, UserType userType, LocalDateTime termsAgreedAt) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.userType = userType;
        this.termsAgreedAt = termsAgreedAt;
    }

    /** 카카오 가입 */
    public static User of(String kakaoId, String nickname) {
        return new User(kakaoId, nickname, UserType.MEMBER, LocalDateTime.now());
    }

    /** 비로그인 체험용 게스트 생성 */
    public static User createGuest() {
        return new User(null, GUEST_NICKNAME, UserType.GUEST, null);
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void withdraw() {
        this.kakaoId = "deleted_" + this.id;
        this.nickname = "탈퇴한 사용자";
        softDelete();
    }

    public boolean isGuest() {
        return this.userType == UserType.GUEST;
    }
}