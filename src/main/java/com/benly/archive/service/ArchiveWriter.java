package com.benly.archive.service;

import com.benly.archive.dto.ArchiveCreateResponse;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.archive.support.ArchiveTime;
import com.benly.global.exception.BusinessException;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import com.benly.user.exception.UserErrorCode;
import com.benly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ArchiveWriter {

    private static final int DAILY_QUESTION_LIMIT = 20;
    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(10);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final InterviewArchiveRepository archiveRepository;
    private final ArchiveQuestionRepository questionRepository;

    public record NewArchive(
            CompanyType companyType,
            InterviewStage stage,
            InterviewFormat format,
            String jobRole,
            YearMonth interviewedMonth,
            String memo,
            List<String> questions
    ) {
    }

    @Transactional
    public ArchiveCreateResponse save(Long userId, ResolvedCompany resolved, NewArchive draft) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        long todayCount = questionRepository.countQuestionsSince(userId, ArchiveTime.startOfTodayKst());
        if (todayCount + draft.questions().size() > DAILY_QUESTION_LIMIT) {
            throw new BusinessException(ArchiveErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        Long companyId = resolved.company().getId();
        rejectDuplicateSubmission(userId, companyId, draft.questions());

        Company company = companyRepository.getReferenceById(companyId);
        InterviewArchive archive = archiveRepository.save(InterviewArchive.create(
                user, company, draft.companyType(), draft.stage(), draft.format(),
                draft.jobRole(), draft.interviewedMonth(), draft.memo()));

        List<Long> questionIds = new ArrayList<>();
        for (int i = 0; i < draft.questions().size(); i++) {
            ArchiveQuestion question = questionRepository.save(
                    ArchiveQuestion.of(archive, i + 1, draft.questions().get(i)));
            questionIds.add(question.getId());
        }

        return new ArchiveCreateResponse(
                archive.getId(),
                companyId,
                resolved.company().getName(),
                resolved.created(),
                questionIds);
    }

    private void rejectDuplicateSubmission(Long userId, Long companyId, List<String> questions) {
        LocalDateTime windowStart = LocalDateTime.now().minus(DUPLICATE_WINDOW);

        boolean duplicated = archiveRepository
                .findByUserIdAndCompanyIdAndCreatedAtAfter(userId, companyId, windowStart).stream()
                .anyMatch(recent -> questionRepository.findByArchiveIdOrderBySeqAsc(recent.getId()).stream()
                        .map(ArchiveQuestion::getContent)
                        .toList()
                        .equals(questions));

        if (duplicated) {
            throw new BusinessException(ArchiveErrorCode.DUPLICATE_SUBMISSION);
        }
    }
}
