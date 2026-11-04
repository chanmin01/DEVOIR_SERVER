package com.benly.archive.controller;

import com.benly.archive.dto.ArchiveCreateRequest;
import com.benly.archive.dto.ArchiveCreateResponse;
import com.benly.archive.dto.ArchiveLikeResponse;
import com.benly.archive.dto.ArchiveListResponse;
import com.benly.archive.dto.ArchiveReportRequest;
import com.benly.archive.dto.ArchiveSearch;
import com.benly.archive.dto.CompanyListResponse;
import com.benly.archive.dto.LikedQuestionListResponse;
import com.benly.archive.dto.MyArchiveListResponse;
import com.benly.archive.service.ArchiveDeleteService;
import com.benly.archive.service.ArchiveLikeService;
import com.benly.archive.service.ArchiveMyPageService;
import com.benly.archive.service.ArchiveQueryService;
import com.benly.archive.service.ArchiveRegisterService;
import com.benly.archive.service.ArchiveReportService;
import com.benly.archive.entity.InterviewFormat;
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
    private final ArchiveLikeService archiveLikeService;
    private final ArchiveReportService archiveReportService;
    private final ArchiveDeleteService archiveDeleteService;
    private final ArchiveMyPageService archiveMyPageService;

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

    @DeleteMapping("/{archiveId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long archiveId) {
        archiveDeleteService.delete(userId, archiveId);
        return ApiResponse.success("등록한 질문을 삭제했어요.");
    }

    @PostMapping("/{archiveId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> report(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long archiveId,
            @Valid @RequestBody ArchiveReportRequest request) {
        archiveReportService.report(userId, archiveId, request.toReason());
        return ApiResponse.created("신고가 접수됐어요.", null);
    }

    @PostMapping("/questions/{questionId}/like")
    public ApiResponse<ArchiveLikeResponse> like(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long questionId) {
        return ApiResponse.success("질문을 저장했어요.", archiveLikeService.like(userId, questionId));
    }

    @DeleteMapping("/questions/{questionId}/like")
    public ApiResponse<ArchiveLikeResponse> unlike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long questionId) {
        return ApiResponse.success("저장을 취소했어요.", archiveLikeService.unlike(userId, questionId));
    }

    @GetMapping("/me")
    public ApiResponse<MyArchiveListResponse> getMyArchives(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success("조회에 성공했습니다.", archiveMyPageService.getMyArchives(userId, cursor, size));
    }

    @GetMapping("/questions/liked")
    public ApiResponse<LikedQuestionListResponse> getLikedQuestions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success("조회에 성공했습니다.", archiveMyPageService.getLikedQuestions(userId, cursor, size));
    }
}
