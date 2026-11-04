package com.benly.pt.dto;

import com.benly.pt.entity.PtTrack;

import java.util.List;
import java.util.Map;

public record PtTaskContent(
        String title,
        String serviceDescription,
        List<String> currentFlow,
        String problemSituation,
        List<String> availableData,
        List<String> constraints,
        List<String> exerciseAssumptions,
        Map<PtTrack, String> trackGuidance,
        List<String> presentationRequirements
) {
}