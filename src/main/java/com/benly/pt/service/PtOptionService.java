package com.benly.pt.service;

import com.benly.pt.dto.PtOptionResponse;
import com.benly.pt.entity.PtTaskStatus;
import com.benly.pt.entity.PtTrack;
import com.benly.pt.policy.PtInterviewRules;
import com.benly.pt.repository.PtTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class PtOptionService {

    private final PtTaskRepository tasks;

    @Transactional(readOnly = true)
    public PtOptionResponse getOptions() {
        var tracks = Arrays.stream(PtTrack.values())
                .map(track -> new PtOptionResponse.TrackOption(
                        track.name(),
                        track.getLabel()
                ))
                .toList();

        return new PtOptionResponse(
                tasks.findAvailableCompanies(PtTaskStatus.APPROVED),
                tracks,
                PtInterviewRules.PREPARATION_MINUTES,
                PtInterviewRules.PRESENTATION_SECONDS,
                PtInterviewRules.MAIN_QUESTION_COUNT,
                PtInterviewRules.FOLLOW_UP_QUESTION_COUNT_PER_MAIN,
                PtInterviewRules.TOTAL_QUESTION_COUNT
        );
    }
}