package com.benly.archive.controller;

import com.benly.archive.dto.CompanySearchResponse;
import com.benly.archive.service.CompanySearchService;
import com.benly.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanySearchService companySearchService;

    @GetMapping
    public ApiResponse<CompanySearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success("조회에 성공했습니다.", companySearchService.search(q, size));
    }
}
