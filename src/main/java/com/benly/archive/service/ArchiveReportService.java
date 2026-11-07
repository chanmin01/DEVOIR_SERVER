package com.benly.archive.service;

import com.benly.archive.entity.ArchiveReport;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.ReportReason;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveReportRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.archive.support.ArchiveReportPolicy;
import com.benly.global.exception.BusinessException;
import com.benly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveReportService {

    private final InterviewArchiveRepository archiveRepository;
    private final ArchiveReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ArchiveReportPolicy reportPolicy;

    @Transactional
    public void report(Long userId, Long archiveId, ReportReason reason) {
        if (archiveId == null || archiveId <= 0) {
            throw new BusinessException(ArchiveErrorCode.INVALID_PATH);
        }
        InterviewArchive archive = archiveRepository.findByIdForUpdate(archiveId)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new BusinessException(ArchiveErrorCode.ARCHIVE_NOT_FOUND));

        if (archive.isOwnedBy(userId)) {
            throw new BusinessException(ArchiveErrorCode.CANNOT_REPORT_OWN_QUESTION);
        }
        if (reportRepository.existsByUserIdAndArchiveId(userId, archiveId)) {
            throw new BusinessException(ArchiveErrorCode.ALREADY_REPORTED);
        }

        reportRepository.save(ArchiveReport.of(userRepository.getReferenceById(userId), archive, reason));
        archive.addReport(reportPolicy.hideThreshold());
    }
}
