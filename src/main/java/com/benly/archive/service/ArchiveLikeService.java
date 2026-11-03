package com.benly.archive.service;

import com.benly.archive.dto.ArchiveLikeResponse;
import com.benly.archive.entity.ArchiveLike;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveLikeRepository;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.global.exception.BusinessException;
import com.benly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveLikeService {

    private final ArchiveQuestionRepository questionRepository;
    private final InterviewArchiveRepository archiveRepository;
    private final ArchiveLikeRepository likeRepository;
    private final UserRepository userRepository;

    @Transactional
    public ArchiveLikeResponse like(Long userId, Long questionId) {
        validatePath(questionId);
        ArchiveQuestion question = questionRepository.findByIdForUpdate(questionId)
                .filter(q -> q.getArchive().isVisible())
                .orElseThrow(() -> new BusinessException(ArchiveErrorCode.QUESTION_NOT_FOUND));
        Long archiveId = question.getArchive().getId();

        if (!likeRepository.existsByUserIdAndQuestionId(userId, questionId)) {
            likeRepository.saveAndFlush(ArchiveLike.of(userRepository.getReferenceById(userId), question));
            questionRepository.increaseLikeCount(questionId);
            archiveRepository.increaseLikeCount(archiveId);
        }
        return new ArchiveLikeResponse(questionId, true, questionRepository.findLikeCount(questionId));
    }

    @Transactional
    public ArchiveLikeResponse unlike(Long userId, Long questionId) {
        validatePath(questionId);
        Long archiveId = questionRepository.findArchiveId(questionId)
                .orElseThrow(() -> new BusinessException(ArchiveErrorCode.QUESTION_NOT_FOUND));

        if (likeRepository.deleteByUserIdAndQuestionId(userId, questionId) > 0) {
            questionRepository.decreaseLikeCount(questionId);
            archiveRepository.decreaseLikeCount(archiveId);
        }
        return new ArchiveLikeResponse(questionId, false, questionRepository.findLikeCount(questionId));
    }

    private void validatePath(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(ArchiveErrorCode.INVALID_PATH);
        }
    }
}
