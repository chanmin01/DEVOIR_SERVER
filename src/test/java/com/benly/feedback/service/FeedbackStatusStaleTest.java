package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackStatusResponse;
import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.repository.*;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.pt.service.PtContentCodec;
import com.benly.session.entity.Session;
import com.benly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class FeedbackStatusStaleTest {

    @Mock private SessionReadRepository sessionReadRepository;
    @Mock private SessionFeedbackRepository sessionFeedbackRepository;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private ScoreAxisRepository scoreAxisRepository;
    @Mock private QuestionReadRepository questionReadRepository;
    @Mock private AnswerReadRepository answerReadRepository;
    @Mock private FeedbackReportAssembler assembler;
    @Mock private StaleTaskPolicy staleTaskPolicy;
    @Mock private SessionFormatResolver formatResolver;
    @Mock private PtSessionRepository ptSessionRepository;
    @Mock private PtContentCodec ptContentCodec;

    @InjectMocks
    private FeedbackQueryService service;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 8L;

    private SessionFeedback scoringSince(LocalDateTime startedAt) {
        User owner = mock(User.class);
        given(owner.getId()).willReturn(USER_ID);
        Session s = Session.create(owner, "SERVICE", "TECHNICAL", "백엔드", "카카오");
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        SessionFeedback sf = SessionFeedback.startScoring(s);
        ReflectionTestUtils.setField(sf, "scoringStartedAt", startedAt);
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID)).willReturn(Optional.of(sf));
        return sf;
    }

    @Test
    @DisplayName("멈춘 채점은 상태 조회에서 FAILED로 내려가고 DB는 바꾸지 않는다")
    void staleReportedAsFailed() {
        LocalDateTime now = LocalDateTime.now();
        scoringSince(now.minusMinutes(10));
        given(staleTaskPolicy.scoringThreshold()).willReturn(now.minusMinutes(5));

        FeedbackStatusResponse response = service.getStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(FeedbackStatus.FAILED);
        verify(sessionFeedbackRepository).findBySession_Id(SESSION_ID);
        verifyNoMoreInteractions(sessionFeedbackRepository);
    }

    @Test
    @DisplayName("정상 진행 중인 채점은 SCORING 그대로 내려간다")
    void activeReportedAsScoring() {
        LocalDateTime now = LocalDateTime.now();
        scoringSince(now.minusMinutes(1));
        given(staleTaskPolicy.scoringThreshold()).willReturn(now.minusMinutes(5));

        FeedbackStatusResponse response = service.getStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(FeedbackStatus.SCORING);
    }
}
