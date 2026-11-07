package com.benly.archive.service;

import com.benly.archive.dto.ArchiveCreateRequest;
import com.benly.archive.dto.ArchiveCreateResponse;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.support.ArchiveTime;
import com.benly.global.exception.BusinessException;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArchiveRegisterService {

    private static final int MIN_QUESTION_LENGTH = 5;
    private static final YearMonth MIN_MONTH = YearMonth.of(2000, 1);

    private final CompanyRepository companyRepository;
    private final CompanyResolver companyResolver;
    private final ArchiveWriter archiveWriter;

    public ArchiveCreateResponse register(Long userId, ArchiveCreateRequest req) {
        InterviewFormat format = InterviewFormat.valueOf(req.format());
        InterviewStage stage = InterviewStage.valueOf(req.stage());
        if (format == InterviewFormat.PT && stage == InterviewStage.PERSONALITY) {
            throw new BusinessException(ArchiveErrorCode.PT_ONLY_TECHNICAL);
        }

        YearMonth month = YearMonth.parse(req.interviewedOn());
        if (month.isAfter(ArchiveTime.currentMonthKst())) {
            throw new BusinessException(ArchiveErrorCode.FUTURE_INTERVIEW_MONTH);
        }
        if (month.isBefore(MIN_MONTH)) {
            throw new BusinessException(ArchiveErrorCode.INVALID_INTERVIEW_MONTH);
        }

        List<String> questions = req.questions().stream().map(String::trim).toList();
        if (questions.stream().anyMatch(q -> q.length() < MIN_QUESTION_LENGTH)) {
            throw new BusinessException(ArchiveErrorCode.QUESTION_TOO_SHORT);
        }
        if (new HashSet<>(questions).size() != questions.size()) {
            throw new BusinessException(ArchiveErrorCode.DUPLICATE_QUESTION_IN_REQUEST);
        }

        ResolvedCompany resolved = resolveCompany(req);

        ArchiveWriter.NewArchive draft = new ArchiveWriter.NewArchive(
                CompanyType.valueOf(req.companyType()),
                stage,
                format,
                blankToNull(req.jobRole()),
                month,
                blankToNull(req.memo()),
                questions);

        return archiveWriter.save(userId, resolved, draft);
    }

    private ResolvedCompany resolveCompany(ArchiveCreateRequest req) {
        boolean hasId = req.companyId() != null;
        boolean hasName = req.companyName() != null && !req.companyName().isBlank();

        if (!hasId && !hasName) {
            throw new BusinessException(ArchiveErrorCode.COMPANY_INPUT_REQUIRED);
        }
        if (hasId && hasName) {
            throw new BusinessException(ArchiveErrorCode.COMPANY_INPUT_CONFLICT);
        }
        if (hasId) {
            Company company = companyRepository.findById(req.companyId())
                    .orElseThrow(() -> new BusinessException(ArchiveErrorCode.COMPANY_NOT_FOUND));
            return new ResolvedCompany(company, false);
        }
        return companyResolver.findOrCreate(req.companyName());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
