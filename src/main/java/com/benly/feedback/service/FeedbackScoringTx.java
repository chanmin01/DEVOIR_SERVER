package com.benly.feedback.service;

import com.benly.feedback.dto.CardSnapshot;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.dto.ScoringResult.TailInput;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.Feedback;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.entity.SessionFormat;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.*;
import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.pt.dto.PtTaskContent;
import com.benly.pt.entity.PtSession;
import com.benly.pt.entity.PtTrack;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.pt.service.PtContentCodec;
import com.benly.question.entity.Answer;
import com.benly.question.entity.Question;
import com.benly.session.entity.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 최종 채점의 DB 작업. 카드 한 장 저장은 CardScoringTx가 하고,
 * 여기서는 채점 계획 읽기, 카드 상태 읽기, 세션 마무리만 한다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackScoringTx {

    private final SessionReadRepository sessionReadRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final FeedbackRepository feedbackRepository;
    private final QuestionReadRepository questionReadRepository;
    private final AnswerReadRepository answerReadRepository;
    private final FeedbackContentParser contentParser;
    private final SessionFormatResolver formatResolver;
    private final PtSessionRepository ptSessionRepository;
    private final PtContentCodec ptContentCodec;
    private final StaleTaskPolicy staleTaskPolicy;

    @Transactional(readOnly = true)
    public ScoringPlan loadPlan(Long sessionId) {
        Session session = sessionReadRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.SESSION_NOT_FOUND));

        SessionFormat format = formatResolver.resolve(sessionId);
        AxisSet axisSet = AxisSet.resolve(session.getStage(), session.getCompanyType());
        ScoringContext ctx = ScoringContext.from(session);

        List<Question> all = questionReadRepository.findBySession_IdOrderBySeqAsc(sessionId);

        Map<Long, String> transcripts = new HashMap<>();
        for (Answer a : answerReadRepository.findBySessionId(sessionId)) {
            if (a.getQuestion() != null) {
                transcripts.put(a.getQuestion().getId(), a.getTranscript());
            }
        }

        Map<Long, List<Question>> tailsByParent = all.stream()
                .filter(q -> q.getParent() != null)
                .collect(Collectors.groupingBy(q -> q.getParent().getId()));

        List<ScoringPlan.MainInput> mains = all.stream()
                .filter(Question::isMain)
                .sorted(Comparator.comparing(Question::getSeq))
                .map(m -> new ScoringPlan.MainInput(m.getId(), toInput(m, tailsByParent, transcripts)))
                .toList();

        PresentationInput presentation = format == SessionFormat.PT ? loadPresentation(sessionId) : null;

        return new ScoringPlan(axisSet.axes(), ctx, axisSet, mains, format, presentation);
    }

    /** 세션의 카드 상태를 읽는다. 완료된 카드는 내용까지 풀어서 돌려준다 */
    @Transactional(readOnly = true)
    public List<CardSnapshot> loadCards(Long sessionId) {
        LocalDateTime staleThreshold = staleTaskPolicy.scoringThreshold();
        return feedbackRepository.findWithQuestionBySessionId(sessionId).stream()
                .map(f -> new CardSnapshot(
                        f.getQuestion() == null ? null : f.getQuestion().getId(),
                        f.isPresentation(),
                        f.getScoringStatus(),
                        f.isScoring() && !f.isStale(staleThreshold),
                        f.getTopicScore(),
                        f.getQuestion() == null ? null : f.getQuestion().getShortTitle(),
                        f.isCompleted() ? contentParser.parse(f.getContent()) : null))
                .toList();
    }

    /** 이 시도가 아직 현재 시도일 때만 세션 채점을 완료로 바꾼다 */
    @Transactional
    public boolean finishSession(Long sessionId, LocalDateTime attempt, int totalScore, SessionSummary summary) {
        SessionFeedback sf = sessionFeedbackRepository.findBySessionIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.REPORT_NOT_FOUND));
        if (!sf.isCurrentAttempt(attempt)) {
            return false;
        }
        sf.complete(totalScore, summary.summary(),
                summary.keyCoachingWeakness(), summary.keyCoachingAction());
        return true;
    }

    /** 내 시도일 때만 실패 처리 */
    @Transactional
    public void markFailed(Long sessionId, LocalDateTime attempt) {
        sessionFeedbackRepository.failAttempt(sessionId, attempt);
    }

    /**
     * 최종 채점이 대기 시간 초과로 실패한 뒤, 남은 카드가 늦게 끝났을 때 쓴다.
     * 세션 채점이 FAILED이고 모든 카드가 COMPLETED면 새 시도를 열고 시도 번호를 돌려준다.
     * 세션 행을 잠그고 판단하므로 카드 여러 장이 동시에 끝나도 한 곳만 연다.
     */
    @Transactional
    public Optional<LocalDateTime> reopenIfAllCardsDone(Long sessionId) {
        Optional<SessionFeedback> found = sessionFeedbackRepository.findBySessionIdForUpdate(sessionId);
        if (found.isEmpty() || !found.get().isFailed()) {
            return Optional.empty();
        }

        long totalCards = questionReadRepository.findBySession_IdOrderBySeqAsc(sessionId).stream()
                .filter(Question::isMain)
                .count()
                + (formatResolver.resolve(sessionId) == SessionFormat.PT ? 1 : 0);
        long completedCards = feedbackRepository.findWithQuestionBySessionId(sessionId).stream()
                .filter(Feedback::isCompleted)
                .count();
        if (completedCards < totalCards) {
            return Optional.empty();
        }

        SessionFeedback sf = found.get();
        sf.resetForRescore();
        return Optional.of(sf.getScoringStartedAt());
    }

    private PresentationInput loadPresentation(Long sessionId) {
        PtSession pt = ptSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.SESSION_NOT_FOUND));
        PtTaskContent task = ptContentCodec.content(pt.getTask().getContentJson());

        PtTrack track = pt.getTrack();
        String guidance = (track == null || task.trackGuidance() == null)
                ? null
                : task.trackGuidance().get(track);

        return new PresentationInput(
                task.title(),
                task.serviceDescription(),
                listOrEmpty(task.currentFlow()),
                task.problemSituation(),
                listOrEmpty(task.availableData()),
                listOrEmpty(task.constraints()),
                listOrEmpty(task.exerciseAssumptions()),
                track == null ? null : track.name(),
                track == null ? null : track.getLabel(),
                guidance,
                listOrEmpty(task.presentationRequirements()),
                pt.getPresentationDurationSec(),
                pt.getPresentationTranscript()
        );
    }

    private List<String> listOrEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    private MainQuestionInput toInput(Question main,
                                      Map<Long, List<Question>> tailsByParent,
                                      Map<Long, String> transcripts) {
        List<Question> tqs = new ArrayList<>(tailsByParent.getOrDefault(main.getId(), List.of()));
        tqs.sort(Comparator.comparing(Question::getSeq));

        List<TailInput> tails = tqs.stream()
                .map(t -> new TailInput(
                        t.getStrategy() == null ? null : t.getStrategy().getLabel(),
                        t.getContent(),
                        transcripts.get(t.getId())))
                .toList();

        return new MainQuestionInput(
                main.getSeq(), main.getContent(), transcripts.get(main.getId()), tails);
    }
}
