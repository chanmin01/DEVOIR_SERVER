package com.benly.archive.service;

import com.benly.archive.dto.ArchiveLikeResponse;
import com.benly.archive.entity.ArchiveLike;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveLikeRepository;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.global.exception.BusinessException;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveLikeServiceTest {

    @Mock private ArchiveQuestionRepository questionRepository;
    @Mock private InterviewArchiveRepository archiveRepository;
    @Mock private ArchiveLikeRepository likeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ArchiveLikeService service;

    private static final Long USER_ID = 1L;
    private static final Long QUESTION_ID = 201L;
    private static final Long ARCHIVE_ID = 41L;

    private void question(boolean visible) {
        InterviewArchive archive = mock(InterviewArchive.class);
        given(archive.getId()).willReturn(ARCHIVE_ID);
        given(archive.isVisible()).willReturn(visible);
        ArchiveQuestion question = mock(ArchiveQuestion.class);
        given(question.getArchive()).willReturn(archive);
        given(questionRepository.findByIdForUpdate(QUESTION_ID)).willReturn(Optional.of(question));
    }

    private void counts(int question, int archive) {
        given(questionRepository.findLikeCount(QUESTION_ID)).willReturn(question);
        given(archiveRepository.findLikeCount(ARCHIVE_ID)).willReturn(archive);
    }

    @Test
    @DisplayName("처음 누르면 하트를 저장하고 질문 하트 수와 면접 기록 합계를 같이 올려 둘 다 응답한다")
    void likeFirstTime() {
        question(true);
        given(likeRepository.existsByUserIdAndQuestionId(USER_ID, QUESTION_ID)).willReturn(false);
        counts(22, 40);

        ArchiveLikeResponse response = service.like(USER_ID, QUESTION_ID);

        verify(likeRepository).saveAndFlush(any(ArchiveLike.class));
        verify(questionRepository).increaseLikeCount(QUESTION_ID);
        verify(archiveRepository).increaseLikeCount(ARCHIVE_ID);
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(22);
        assertThat(response.archiveLikeCount()).isEqualTo(40);
    }

    @Test
    @DisplayName("이미 눌렀으면 숫자를 올리지 않고 같은 응답을 준다")
    void likeIdempotent() {
        question(true);
        given(likeRepository.existsByUserIdAndQuestionId(USER_ID, QUESTION_ID)).willReturn(true);
        counts(22, 40);

        ArchiveLikeResponse response = service.like(USER_ID, QUESTION_ID);

        verify(likeRepository, never()).saveAndFlush(any());
        verify(questionRepository, never()).increaseLikeCount(anyLong());
        verify(archiveRepository, never()).increaseLikeCount(anyLong());
        assertThat(response.liked()).isTrue();
    }

    @Test
    @DisplayName("숨김이나 삭제된 기록의 질문은 하트를 누를 수 없다")
    void likeHiddenRejected() {
        question(false);

        assertThatThrownBy(() -> service.like(USER_ID, QUESTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 질문이면 하트와 취소 모두 404")
    void notFound() {
        given(questionRepository.findByIdForUpdate(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(USER_ID, QUESTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.QUESTION_NOT_FOUND);
        assertThatThrownBy(() -> service.unlike(USER_ID, QUESTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("questionId가 0 이하면 400")
    void invalidPath() {
        assertThatThrownBy(() -> service.like(USER_ID, 0L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.INVALID_PATH);
        assertThatThrownBy(() -> service.unlike(USER_ID, -1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.INVALID_PATH);
    }

    @Test
    @DisplayName("취소도 질문 행을 잠그고, 지웠을 때만 두 숫자를 내린다")
    void unlikeDeleted() {
        question(true);
        given(likeRepository.deleteByUserIdAndQuestionId(USER_ID, QUESTION_ID)).willReturn(1);
        counts(21, 39);

        ArchiveLikeResponse response = service.unlike(USER_ID, QUESTION_ID);

        verify(questionRepository).findByIdForUpdate(QUESTION_ID);
        verify(questionRepository).decreaseLikeCount(QUESTION_ID);
        verify(archiveRepository).decreaseLikeCount(ARCHIVE_ID);
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(21);
        assertThat(response.archiveLikeCount()).isEqualTo(39);
    }

    @Test
    @DisplayName("안 눌렀던 질문을 취소하면 숫자를 건드리지 않는다")
    void unlikeIdempotent() {
        question(true);
        given(likeRepository.deleteByUserIdAndQuestionId(USER_ID, QUESTION_ID)).willReturn(0);

        ArchiveLikeResponse response = service.unlike(USER_ID, QUESTION_ID);

        verify(questionRepository, never()).decreaseLikeCount(anyLong());
        verify(archiveRepository, never()).decreaseLikeCount(anyLong());
        assertThat(response.liked()).isFalse();
    }

    @Test
    @DisplayName("숨김이나 삭제된 기록의 질문도 취소는 허용한다")
    void unlikeHiddenAllowed() {
        question(false);
        given(likeRepository.deleteByUserIdAndQuestionId(USER_ID, QUESTION_ID)).willReturn(1);

        ArchiveLikeResponse response = service.unlike(USER_ID, QUESTION_ID);

        verify(questionRepository).decreaseLikeCount(QUESTION_ID);
        assertThat(response.liked()).isFalse();
    }
}
