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

    // 이미 눌렀으면 그대로 성공. 숨김, 삭제된 기록의 질문은 막는다
    @Transactional
    public ArchiveLikeResponse like(Long userId, Long questionId) {
        ArchiveQuestion question = lockQuestion(questionId);
        if (!question.getArchive().isVisible()) {
            throw new BusinessException(ArchiveErrorCode.QUESTION_NOT_FOUND);
        }
        Long archiveId = question.getArchive().getId();

        if (!likeRepository.existsByUserIdAndQuestionId(userId, questionId)) {
            likeRepository.saveAndFlush(ArchiveLike.of(userRepository.getReferenceById(userId), question));
            questionRepository.increaseLikeCount(questionId);
            archiveRepository.increaseLikeCount(archiveId);
        }
        return response(questionId, archiveId, true);
    }

    // 지워진 행이 있을 때만 숫자를 내린다. 숨김, 삭제된 기록의 질문도 취소는 허용
    @Transactional
    public ArchiveLikeResponse unlike(Long userId, Long questionId) {
        ArchiveQuestion question = lockQuestion(questionId);
        Long archiveId = question.getArchive().getId();

        if (likeRepository.deleteByUserIdAndQuestionId(userId, questionId) > 0) {
            questionRepository.decreaseLikeCount(questionId);
            archiveRepository.decreaseLikeCount(archiveId);
        }
        return response(questionId, archiveId, false);
    }

    // like와 unlike가 같은 질문 행 잠금으로 한 줄로 선다
    private ArchiveQuestion lockQuestion(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(ArchiveErrorCode.INVALID_PATH);
        }
        return questionRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ArchiveErrorCode.QUESTION_NOT_FOUND));
    }

    private ArchiveLikeResponse response(Long questionId, Long archiveId, boolean liked) {
        return new ArchiveLikeResponse(questionId, liked,
                questionRepository.findLikeCount(questionId),
                archiveRepository.findLikeCount(archiveId));
    }
}
