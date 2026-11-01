package com.benly.archive.controller;

import com.benly.archive.dto.ArchiveCreateRequest;
import com.benly.archive.dto.ArchiveCreateResponse;
import com.benly.archive.dto.ArchiveListResponse;
import com.benly.archive.dto.ArchiveSearch;
import com.benly.archive.dto.CompanyListResponse;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.service.ArchiveQueryService;
import com.benly.archive.service.ArchiveRegisterService;
import com.benly.archive.support.ArchiveSort;
import com.benly.global.common.ApiResponse;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/archives")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveRegisterService archiveRegisterService;
    private final ArchiveQueryService archiveQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArchiveCreateResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ArchiveCreateRequest request) {
        return ApiResponse.created("질문을 등록했어요.", archiveRegisterService.register(userId, request));
    }

    @GetMapping
    public ApiResponse<ArchiveListResponse> getArchives(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) InterviewStage stage,
            @RequestParam(required = false) InterviewFormat format,
            @RequestParam(required = false) String jobRole,
            @RequestParam(required = false) ArchiveSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        ArchiveSearch search = new ArchiveSearch(companyId, companyType, stage, format, jobRole, sort, cursor, size);
        return ApiResponse.success("조회에 성공했습니다.", archiveQueryService.getArchives(userId, search));
    }

    @GetMapping("/companies")
    public ApiResponse<CompanyListResponse> getCompanies(
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success("조회에 성공했습니다.", archiveQueryService.getCompanies(companyType, cursor, size));
    }
}
