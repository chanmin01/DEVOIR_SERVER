package com.benly.archive.service;

import com.benly.archive.entity.ArchiveReport;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.ReportReason;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveReportRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveReportServiceTest {

    @Mock private InterviewArchiveRepository archiveRepository;
    @Mock private ArchiveReportRepository reportRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ArchiveReportService service;

    private static final Long USER_ID = 1L;
    private static final Long ARCHIVE_ID = 41L;

    private InterviewArchive archive(boolean visible, boolean mine) {
        InterviewArchive archive = mock(InterviewArchive.class);
        given(archive.isVisible()).willReturn(visible);
        given(archive.isOwnedBy(USER_ID)).willReturn(mine);
        given(archiveRepository.findByIdForUpdate(ARCHIVE_ID)).willReturn(Optional.of(archive));
        return archive;
    }

    @Test
    @DisplayName("신고를 저장하고 신고 수를 올린다")
    void report() {
        InterviewArchive archive = archive(true, false);
        given(reportRepository.existsByUserIdAndArchiveId(USER_ID, ARCHIVE_ID)).willReturn(false);

        service.report(USER_ID, ARCHIVE_ID, ReportReason.AD);

        verify(reportRepository).save(any(ArchiveReport.class));
        verify(archive).addReport(ArchiveReportService.HIDE_THRESHOLD);
    }

    @Test
    @DisplayName("내 기록은 신고할 수 없다")
    void rejectOwn() {
        InterviewArchive archive = archive(true, true);

        assertThatThrownBy(() -> service.report(USER_ID, ARCHIVE_ID, ReportReason.AD))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.CANNOT_REPORT_OWN_QUESTION);
        verify(archive, never()).addReport(anyInt());
    }

    @Test
    @DisplayName("이미 신고했으면 409")
    void rejectDuplicate() {
        InterviewArchive archive = archive(true, false);
        given(reportRepository.existsByUserIdAndArchiveId(USER_ID, ARCHIVE_ID)).willReturn(true);

        assertThatThrownBy(() -> service.report(USER_ID, ARCHIVE_ID, ReportReason.AD))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.ALREADY_REPORTED);
        verify(reportRepository, never()).save(any());
        verify(archive, never()).addReport(anyInt());
    }

    @Test
    @DisplayName("숨김이나 삭제된 기록은 404")
    void rejectHidden() {
        archive(false, false);

        assertThatThrownBy(() -> service.report(USER_ID, ARCHIVE_ID, ReportReason.AD))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.ARCHIVE_NOT_FOUND);
    }

    @Test
    @DisplayName("archiveId가 0 이하면 400")
    void invalidPath() {
        assertThatThrownBy(() -> service.report(USER_ID, 0L, ReportReason.AD))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.INVALID_PATH);
    }
}
