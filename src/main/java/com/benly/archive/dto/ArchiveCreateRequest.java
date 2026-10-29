package com.benly.archive.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record ArchiveCreateRequest(

        Long companyId,

        @Size(max = 50, message = "회사명은 50자까지 입력할 수 있습니다.")
        String companyName,

        @NotBlank(message = "기업 유형을 선택해 주세요.")
        @Pattern(regexp = "BIG_TECH_SW|SERVICE|FINANCE_IT", message = "기업 유형을 선택해 주세요.")
        String companyType,

        @NotBlank(message = "면접 단계를 선택해 주세요.")
        @Pattern(regexp = "TECHNICAL|PERSONALITY", message = "면접 단계를 선택해 주세요.")
        String stage,

        @NotBlank(message = "면접 형식을 선택해 주세요.")
        @Pattern(regexp = "QA|PT", message = "면접 형식을 선택해 주세요.")
        String format,

        @Size(max = 30, message = "허용되지 않는 직무입니다.")
        String jobRole,

        @NotBlank(message = "면접 시기를 입력해 주세요.")
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "면접 시기는 YYYY-MM 형식으로 입력해 주세요.")
        String interviewedOn,

        @Size(max = 500, message = "메모는 500자까지 입력할 수 있습니다.")
        String memo,

        @NotEmpty(message = "질문을 1개 이상 입력해 주세요.")
        @Size(max = 10, message = "질문은 한 번에 10개까지 등록할 수 있습니다.")
        List<
                @NotBlank(message = "빈 질문이 있습니다.")
                @Size(max = 500, message = "질문은 500자까지 입력할 수 있습니다.")
                        String> questions,

        @NotNull(message = "본인이 직접 경험한 질문인지 확인해 주세요.")
        @AssertTrue(message = "본인이 직접 경험한 질문인지 확인해 주세요.")
        Boolean firsthandConfirmed
) {
}
