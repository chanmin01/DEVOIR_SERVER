package com.benly.user.service;

import com.benly.auth.repository.RefreshTokenRepository;
import com.benly.document.entity.Document;
import com.benly.document.repository.DocumentRepository;
import com.benly.global.exception.BusinessException;
import com.benly.user.dto.UserMeResponse;
import com.benly.user.entity.User;
import com.benly.user.event.UserWithdrawnEvent;
import com.benly.user.exception.UserErrorCode;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("활성 유저의 닉네임이 정상적으로 변경된다")
    void updateNicknameSuccess() {
        // given
        User user = User.of("12345", "기존닉네임");
        given(userRepository.findByIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.of(user));

        // when
        userService.updateNickname(1L, "새닉네임");

        // then
        assertThat(user.getNickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("존재하지 않는(탈퇴) 유저의 닉네임 변경 시 예외가 발생한다")
    void updateNicknameUserNotFound() {
        // given
        given(userRepository.findByIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(1L, "새닉네임"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원 탈퇴 시 개인정보가 비식별화되고 토큰 삭제, 탈퇴 이벤트가 발행된다")
    void withdrawSuccess() {
        // given
        User user = User.of("12345", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(documentRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());

        // when
        userService.withdraw(1L);

        // then
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getKakaoId()).isEqualTo("deleted_1");
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자");
        verify(refreshTokenRepository).deleteByUserId(1L);
        verify(eventPublisher).publishEvent(new UserWithdrawnEvent("12345", List.of()));
    }

    @Test
    @DisplayName("회원 탈퇴 시 지원 서류가 소프트 삭제되고 파일 키가 이벤트에 담긴다")
    void withdrawDeletesDocuments() {
        // given
        User user = User.of("12345", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        Document doc = Mockito.mock(Document.class);
        given(doc.getStorageKey()).willReturn("documents/1/resume.pdf");
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(documentRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of(doc));

        // when
        userService.withdraw(1L);

        // then
        verify(doc).softDelete();
        verify(eventPublisher).publishEvent(
                new UserWithdrawnEvent("12345", List.of("documents/1/resume.pdf")));
    }

    @Test
    @DisplayName("존재하지 않는 유저 탈퇴 시 예외가 발생하고 토큰은 건드리지 않는다")
    void withdrawUserNotFound() {
        // given
        given(userRepository.findByIdForUpdate(1L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.withdraw(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        verify(refreshTokenRepository, never()).deleteByUserId(1L);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("내 정보 조회 시 id와 닉네임을 반환한다")
    void getMyInfoSuccess() {
        // given
        User user = User.of("12345", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findByIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.of(user));

        // when
        UserMeResponse response = userService.getMyInfo(1L);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("존재하지 않는(탈퇴) 유저의 정보 조회 시 예외가 발생한다")
    void getMyInfoUserNotFound() {
        // given
        given(userRepository.findByIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }

}
