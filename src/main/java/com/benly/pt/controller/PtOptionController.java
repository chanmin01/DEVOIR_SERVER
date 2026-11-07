package com.benly.pt.controller;

import com.benly.global.common.ApiResponse;
import com.benly.pt.dto.PtOptionResponse;
import com.benly.pt.service.PtOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pt")
public class PtOptionController {

    private final PtOptionService service;

    @GetMapping("/options")
    public ApiResponse<PtOptionResponse> options() {
        return ApiResponse.success(
                "PT 설정을 조회했습니다.",
                service.getOptions()
        );
    }
}