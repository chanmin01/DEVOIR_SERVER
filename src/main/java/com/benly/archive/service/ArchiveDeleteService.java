package com.benly.archive.service;

import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveDeleteService {

    private final InterviewArchiveRepository archiveRepository;

    @Transactional
    public void delete(Long userId, Long archiveId) {
        if (archiveId == null || archiveId <= 0) {
            throw new BusinessException(ArchiveErrorCode.INVALID_PATH);
        }
        InterviewArchive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BusinessException(ArchiveErrorCode.ARCHIVE_NOT_FOUND));

        if (!archive.isOwnedBy(userId)) {
            throw new BusinessException(ArchiveErrorCode.ARCHIVE_FORBIDDEN);
        }
        if (!archive.isDeleted()) {
            archive.softDelete();
        }
    }
}
