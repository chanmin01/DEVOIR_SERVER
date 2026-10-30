package com.benly.archive.service;

import com.benly.archive.dto.CompanyCandidate;
import com.benly.archive.dto.CompanySearchResponse;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.CompanyAliasRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.support.CompanyNameNormalizer;
import com.benly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanySearchService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private static final int MIN_KEY_LENGTH = 2;
    private static final int MAX_KEY_LENGTH = 50;
    private static final int CANDIDATE_LIMIT = 50;

    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;

    public CompanySearchResponse search(String q, Integer size) {
        if (q == null || q.isBlank()) {
            throw new BusinessException(ArchiveErrorCode.SEARCH_KEYWORD_REQUIRED);
        }
        int limit = (size == null) ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw new BusinessException(ArchiveErrorCode.INVALID_SEARCH_SIZE);
        }

        String key = CompanyNameNormalizer.normalize(q);
        if (key.length() < MIN_KEY_LENGTH) {
            throw new BusinessException(ArchiveErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ArchiveErrorCode.SEARCH_KEYWORD_TOO_LONG);
        }

        Pageable candidates = PageRequest.of(0, CANDIDATE_LIMIT);
        Map<Long, CompanyCandidate> merged = new LinkedHashMap<>();
        companyRepository.searchByName(key, candidates)
                .forEach(c -> merged.put(c.companyId(), c));
        companyAliasRepository.searchByAlias(key, candidates)
                .forEach(c -> merged.merge(c.companyId(), c, (current, alias) -> preferExact(current, alias, key)));

        List<CompanySearchResponse.Item> items = merged.values().stream()
                .sorted(Comparator
                        .comparing((CompanyCandidate c) -> !c.isExactMatch(key))
                        .thenComparing(CompanyCandidate::questionCount, Comparator.reverseOrder())
                        .thenComparing(CompanyCandidate::name))
                .limit(limit)
                .map(CompanySearchResponse.Item::from)
                .toList();

        return new CompanySearchResponse(items);
    }

    private CompanyCandidate preferExact(CompanyCandidate current, CompanyCandidate incoming, String key) {
        if (!current.isExactMatch(key) && incoming.isExactMatch(key)) {
            return incoming;
        }
        return current;
    }
}
