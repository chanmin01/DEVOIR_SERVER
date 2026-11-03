package com.benly.pt.service;

import com.benly.pt.dto.PtTaskContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PtContentCodec {

    private final ObjectMapper mapper;

    public String content(PtTaskContent value) {
        return mapper.writeValueAsString(value);
    }

    public PtTaskContent content(String value) {
        return mapper.readValue(value, PtTaskContent.class);
    }
}