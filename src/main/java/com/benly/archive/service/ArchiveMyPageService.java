package com.benly.archive.service;

import com.benly.archive.dto.LikedQuestionListResponse;
import com.benly.archive.dto.MyArchiveListResponse;
import com.benly.archive.entity.ArchiveLike;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.repository.ArchiveLikeRepository;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.archive.support.ArchiveCursor;
import com.benly.archive.support.ArchivePageSize;
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
public class ArchiveMyPageService {

    private final InterviewArchiveRepository archiveRepository;
    private final ArchiveQuestionRepository questionRepository;
    private final ArchiveLikeRepository likeRepository;

    public MyArchiveListResponse getMyArchives(Long userId, String rawCursor, Integer rawSize) {
        int size = ArchivePageSize.resolve(rawSize);
        ArchiveCursor cursor = ArchiveCursor.ofId(rawCursor);

        List<InterviewArchive> rows = archiveRepository.findMine(userId,
                cursor == null ? null : cursor.id(), PageRequest.of(0, size + 1));
        long totalQuestions = questionRepository.countMine(userId);

        boolean hasNext = rows.size() > size;
        List<InterviewArchive> page = hasNext ? rows.subList(0, size) : rows;
        if (page.isEmpty()) {
            return MyArchiveListResponse.empty(totalQuestions);
        }

        List<Long> archiveIds = page.stream().map(InterviewArchive::getId).toList();
        List<ArchiveQuestion> questions = questionRepository.findByArchiveIds(archiveIds);
        Map<Long, List<ArchiveQuestion>> questionsByArchive = questions.stream()
                .collect(Collectors.groupingBy(q -> q.getArchive().getId()));

        List<Long> questionIds = questions.stream().map(ArchiveQuestion::getId).toList();
        Set<Long> likedIds = questionIds.isEmpty()
                ? Set.of()
                : new HashSet<>(likeRepository.findLikedQuestionIds(userId, questionIds));

        List<MyArchiveListResponse.Item> items = page.stream()
                .map(a -> MyArchiveListResponse.Item.of(a,
                        questionsByArchive.getOrDefault(a.getId(), List.of()), likedIds))
                .toList();
        String nextCursor = hasNext ? ArchiveCursor.encode(page.get(page.size() - 1).getId()) : null;
        return new MyArchiveListResponse(totalQuestions, items, nextCursor, hasNext);
    }

    public LikedQuestionListResponse getLikedQuestions(Long userId, String rawCursor, Integer rawSize) {
        int size = ArchivePageSize.resolve(rawSize);
        ArchiveCursor cursor = ArchiveCursor.ofId(rawCursor);

        List<ArchiveLike> rows = likeRepository.findVisibleLikes(userId,
                cursor == null ? null : cursor.id(), PageRequest.of(0, size + 1));
        long totalCount = likeRepository.countVisibleLikes(userId);

        boolean hasNext = rows.size() > size;
        List<ArchiveLike> page = hasNext ? rows.subList(0, size) : rows;
        if (page.isEmpty()) {
            return LikedQuestionListResponse.empty(totalCount);
        }

        List<LikedQuestionListResponse.Item> items = page.stream()
                .map(l -> LikedQuestionListResponse.Item.of(l, userId))
                .toList();
        String nextCursor = hasNext ? ArchiveCursor.encode(page.get(page.size() - 1).getId()) : null;
        return new LikedQuestionListResponse(totalCount, items, nextCursor, hasNext);
    }
}
