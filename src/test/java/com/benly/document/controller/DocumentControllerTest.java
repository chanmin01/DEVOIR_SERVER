package com.benly.document.controller;


import com.benly.pt.repository.PtSessionRepository;
import com.benly.document.dto.DocumentResponse;
import com.benly.document.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @MockitoBean
    private PtSessionRepository ptSessionRepository;


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;


    @Test
    @DisplayName("서류 목록 조회 - 성공")
    void getDocument_success() throws Exception {
        //given
        List<DocumentResponse> fakeList = List.of(
                new DocumentResponse(1L, "카카오지원서.pdf", LocalDateTime.now()),
                new DocumentResponse(2L, "백엔드공통.pdf", LocalDateTime.now())
        );
        given(documentService.getDocuments(anyLong())).willReturn(fakeList);

        //when & then
        mockMvc.perform(get("/api/v1/documents").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())                        // 배열인가
                .andExpect(jsonPath("$.data[0].docId").value(1))               // 첫 번째 id
                .andExpect(jsonPath("$.data[0].fileName").value("카카오지원서.pdf"))
                .andExpect(jsonPath("$.data[1].fileName").value("백엔드공통.pdf"));
    }
}