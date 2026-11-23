package com.benly.history.dto;

import com.benly.feedback.entity.SessionFeedback;
import com.benly.global.common.KstTime;
import com.benly.session.entity.Session;

import java.util.List;
import java.util.Set;

public record SessionHistoryResponse(
        long totalCount,
        long weekCount,
        List<Item> sessions
) {
    public static SessionHistoryResponse of(long totalCount, long weekCount,
                                            List<SessionFeedback> feedbacks, Set<Long> ptSessionIds) {
        List<Item> items = feedbacks.stream()
                .map(sf -> Item.from(sf, ptSessionIds))
                .toList();
        return new SessionHistoryResponse(totalCount, weekCount, items);
    }

    public record Item(
            Long sessionId,
            String companyType,
            String interviewStage,
            String format,
            String companyName,
            String jobRole,
            String status,
            String createdAt,
            Integer totalScore
    ) {
        private static final String FORMAT_QA = "QA";
        private static final String FORMAT_PT = "PT";

        public static Item from(SessionFeedback sf, Set<Long> ptSessionIds) {
            Session s = sf.getSession();
            return new Item(
                    s.getId(),
                    s.getCompanyType(),
                    s.getStage(),
                    ptSessionIds.contains(s.getId()) ? FORMAT_PT : FORMAT_QA,
                    s.getCompanyName(),
                    s.getJobTitle(),
                    s.getStatus().name(),
                    KstTime.format(s.getCreatedAt()),
                    sf.getTotalScore()
            );
        }
    }
}
