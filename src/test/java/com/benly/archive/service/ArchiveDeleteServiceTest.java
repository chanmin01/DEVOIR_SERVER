package com.benly.archive.service;

import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveDeleteServiceTest {

    @Mock private InterviewArchiveRepository archiveRepository;

    @InjectMocks
    private ArchiveDeleteService service;

    private static final Long USER_ID = 1L;
    private static final Long ARCHIVE_ID = 41L;

    private InterviewArchive archive(boolean mine, boolean deleted) {
        InterviewArchive archive = mock(InterviewArchive.class);
        given(archive.isOwnedBy(USER_ID)).willReturn(mine);
        given(archive.isDeleted()).willReturn(deleted);
        given(archiveRepository.findById(ARCHIVE_ID)).willReturn(Optional.of(archive));
        return archive;
    }

    @Test
    @DisplayName("내 기록이면 soft delete")
    void delete() {
        InterviewArchive archive = archive(true, false);

        service.delete(USER_ID, ARCHIVE_ID);

        verify(archive).softDelete();
    }

    @Test
    @DisplayName("이미 지운 내 기록이면 그대로 성공")
    void deleteIdempotent() {
        InterviewArchive archive = archive(true, true);

        service.delete(USER_ID, ARCHIVE_ID);

        verify(archive, never()).softDelete();
    }

    @Test
    @DisplayName("남의 기록이면 403")
    void rejectOthers() {
        InterviewArchive archive = archive(false, false);

        assertThatThrownBy(() -> service.delete(USER_ID, ARCHIVE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.ARCHIVE_FORBIDDEN);
        verify(archive, never()).softDelete();
    }

    @Test
    @DisplayName("없는 기록이면 404, 0 이하면 400")
    void notFoundAndInvalid() {
        given(archiveRepository.findById(ARCHIVE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, ARCHIVE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.ARCHIVE_NOT_FOUND);
        assertThatThrownBy(() -> service.delete(USER_ID, 0L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.INVALID_PATH);
    }
}
