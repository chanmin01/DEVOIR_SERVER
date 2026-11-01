package com.benly.archive.service;

import com.benly.archive.dto.ArchiveCard;
import com.benly.archive.dto.ArchiveListResponse;
import com.benly.archive.dto.ArchiveSearch;
import com.benly.archive.dto.CompanyListResponse;
import com.benly.archive.dto.CompanyQuestionCount;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveLikeRepository;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.ArchiveReportRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.archive.support.ArchiveCursor;
import com.benly.archive.support.ArchiveSort;
import com.benly.global.exception.BusinessException;
import com.benly.session.entity.CompanyType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final InterviewArchiveRepository archiveRepository;
    private final ArchiveQuestionRepository questionRepository;
    private final ArchiveLikeRepository likeRepository;
    private final ArchiveReportRepository reportRepository;
    private final CompanyRepository companyRepository;

    public ArchiveListResponse getArchives(Long userId, ArchiveSearch search) {
        int size = resolveSize(search.size());
        validateCompany(search.companyId());
        ArchiveSort sort = search.sort() == null ? ArchiveSort.LATEST : search.sort();
        String jobRole = (search.jobRole() == null || search.jobRole().isBlank()) ? null : search.jobRole().trim();
        PageRequest limit = PageRequest.of(0, size + 1);

        List<InterviewArchive> rows;
        if (sort == ArchiveSort.POPULAR) {
            ArchiveCursor cursor = ArchiveCursor.ofScoreAndId(search.cursor());
            rows = archiveRepository.findPopular(search.companyId(), search.companyType(), search.stage(),
                    search.format(), jobRole,
                    cursor == null ? null : toLikeCount(cursor.score()),
                    cursor == null ? null : cursor.id(), limit);
        } else {
            ArchiveCursor cursor = ArchiveCursor.ofId(search.cursor());
            rows = archiveRepository.findLatest(search.companyId(), search.companyType(), search.stage(),
                    search.format(), jobRole,
                    cursor == null ? null : cursor.id(), limit);
        }

        boolean hasNext = rows.size() > size;
        List<InterviewArchive> archives = hasNext ? rows.subList(0, size) : rows;
        if (archives.isEmpty()) {
            return ArchiveListResponse.empty();
        }

        List<Long> archiveIds = archives.stream().map(InterviewArchive::getId).toList();
        Map<Long, List<ArchiveQuestion>> questionsByArchive = questionRepository.findByArchiveIds(archiveIds).stream()
                .collect(Collectors.groupingBy(q -> q.getArchive().getId()));

        List<Long> questionIds = questionsByArchive.values().stream()
                .flatMap(List::stream).map(ArchiveQuestion::getId).toList();
        Set<Long> likedIds = questionIds.isEmpty()
                ? Set.of()
                : new HashSet<>(likeRepository.findLikedQuestionIds(userId, questionIds));
        Set<Long> reportedIds = new HashSet<>(reportRepository.findReportedArchiveIds(userId, archiveIds));

        List<ArchiveCard> cards = archives.stream()
                .map(a -> ArchiveCard.of(a,
                        questionsByArchive.getOrDefault(a.getId(), List.of()),
                        likedIds,
                        a.isOwnedBy(userId),
                        reportedIds.contains(a.getId())))
                .toList();

        String nextCursor = null;
        if (hasNext) {
            InterviewArchive last = archives.get(archives.size() - 1);
            nextCursor = sort == ArchiveSort.POPULAR
                    ? ArchiveCursor.encode(last.getLikeCount(), last.getId())
                    : ArchiveCursor.encode(last.getId());
        }
        return new ArchiveListResponse(cards, nextCursor, hasNext);
    }

    public CompanyListResponse getCompanies(CompanyType companyType, String rawCursor, Integer rawSize) {
        int size = resolveSize(rawSize);
        ArchiveCursor cursor = ArchiveCursor.ofScoreAndId(rawCursor);

        List<CompanyQuestionCount> rows = questionRepository.countByCompany(companyType,
                cursor == null ? null : cursor.score(),
                cursor == null ? null : cursor.id(),
                PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<CompanyQuestionCount> page = hasNext ? rows.subList(0, size) : rows;

        String nextCursor = null;
        if (hasNext) {
            CompanyQuestionCount last = page.get(page.size() - 1);
            nextCursor = ArchiveCursor.encode(last.questionCount(), last.companyId());
        }
        return new CompanyListResponse(
                page.stream().map(CompanyListResponse.Item::from).toList(), nextCursor, hasNext);
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ArchiveErrorCode.INVALID_PAGE_SIZE);
        }
        return size;
    }

    private void validateCompany(Long companyId) {
        if (companyId == null) {
            return;
        }
        if (companyId <= 0) {
            throw new BusinessException(ArchiveErrorCode.INVALID_COMPANY_ID);
        }
        if (!companyRepository.existsById(companyId)) {
            throw new BusinessException(ArchiveErrorCode.COMPANY_NOT_FOUND);
        }
    }

    private Integer toLikeCount(Long score) {
        if (score > Integer.MAX_VALUE) {
            throw new BusinessException(ArchiveErrorCode.INVALID_CURSOR);
        }
        return score.intValue();
    }
}
