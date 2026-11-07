package com.benly.feedback.service;

import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.TailInput;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.CardType;
import com.benly.feedback.entity.Feedback;
import com.benly.feedback.entity.ScoreAxis;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.AnswerReadRepository;
import com.benly.feedback.repository.FeedbackRepository;
import com.benly.feedback.repository.QuestionReadRepository;
import com.benly.feedback.repository.ScoreAxisRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 카드 한 장 채점의 DB 작업만 모은다. Claude 호출은 이 트랜잭션 밖에서 한다.
 * 잡기(claim) → 트랜잭션 밖에서 채점 → 저장(complete) 또는 실패(fail) 순서로 쓴다.
 * 잡을 때 기록한 scoringStartedAt이 시도 번호다. 저장과 실패는 카드를 잠근 뒤 이 번호가 그대로일 때만 반영한다.
 */
@Service
@RequiredArgsConstructor
public class CardScoringTx {

    private final FeedbackRepository feedbackRepository;
    private final ScoreAxisRepository scoreAxisRepository;
    private final QuestionReadRepository questionReadRepository;
    private final AnswerReadRepository answerReadRepository;
    private final PtSessionRepository ptSessionRepository;
    private final PtContentCodec ptContentCodec;
    private final FeedbackContentParser contentParser;
    private final StaleTaskPolicy staleTaskPolicy;

    public record TopicPlan(Long feedbackId, LocalDateTime attempt, Long mainQuestionId,
                            ScoringContext ctx, AxisSet axisSet, MainQuestionInput input) {
    }

    public record PresentationPlan(Long feedbackId, LocalDateTime attempt,
                                   ScoringContext ctx, PresentationInput input) {
    }

    // ===== 잡기 =====

    /**
     * 질문 카드를 채점 상태로 잡는다. 이미 끝났거나 다른 곳에서 채점 중이면 빈 값.
     * 기존 카드는 행을 잠근 뒤 판단해서 한 작업만 재시작한다.
     * 카드가 없을 때 두 곳에서 동시에 만들면 question_id 유니크 제약에 걸려 예외가 나고, 호출한 쪽이 건너뛴다.
     */
    @Transactional
    public Optional<TopicPlan> claimTopic(Long mainQuestionId) {
        Question main = questionReadRepository.findById(mainQuestionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.QUESTION_NOT_FOUND));
        if (!main.isMain()) {
            return Optional.empty();
        }

        Feedback card = claim(feedbackRepository.findByQuestionIdForUpdate(mainQuestionId),
                () -> Feedback.scoringQa(main));
        if (card == null) {
            return Optional.empty();
        }

        Session session = main.getSession();
        return Optional.of(new TopicPlan(
                card.getId(),
                card.getScoringStartedAt(),
                mainQuestionId,
                ScoringContext.from(session),
                AxisSet.resolve(session.getStage(), session.getCompanyType()),
                topicInput(session.getId(), main)));
    }

    /**
     * 발표 카드를 채점 상태로 잡는다. 질문이 없어 유니크 제약을 쓸 수 없으므로
     * PT 세션 행을 잠근 뒤 확인한다.
     */
    @Transactional
    public Optional<PresentationPlan> claimPresentation(Long sessionId) {
        PtSession pt = ptSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.SESSION_NOT_FOUND));
        Session session = pt.getSession();

        Feedback card = claim(
                feedbackRepository.findBySession_IdAndCardType(sessionId, CardType.PRESENTATION),
                () -> Feedback.scoringPresentation(session));
        if (card == null) {
            return Optional.empty();
        }

        return Optional.of(new PresentationPlan(
                card.getId(), card.getScoringStartedAt(),
                ScoringContext.from(session), presentationInput(pt)));
    }

    /** 카드가 없으면 새로 만들고, 실패했거나 오래 멈췄으면 다시 잡는다. 나머지는 null */
    private Feedback claim(Optional<Feedback> existing, Supplier<Feedback> creator) {
        if (existing.isEmpty()) {
            return feedbackRepository.saveAndFlush(creator.get());
        }
        Feedback card = existing.get();
        if (card.needsRescore(staleTaskPolicy.scoringThreshold())) {
            card.restart();
            return card;
        }
        return null;
    }

    // ===== 저장 =====

    /** 이 시도가 아직 유효하면 저장하고 true. 다른 시도가 가져갔으면 버리고 false */
    @Transactional
    public boolean completeTopic(Long feedbackId, LocalDateTime attempt, ScoredTopic topic) {
        Optional<Feedback> owned = ownedCard(feedbackId, attempt);
        if (owned.isEmpty()) {
            return false;
        }
        Feedback card = owned.get();
        card.getQuestion().assignShortTitle(topic.score().shortTitle());
        card.complete(contentParser.toJson(topic.score().content()), topic.topicScore());
        replaceAxes(card, topic.score().axisScores());
        return true;
    }

    @Transactional
    public boolean completePresentation(Long feedbackId, LocalDateTime attempt,
                                        ScoredPresentation presentation) {
        Optional<Feedback> owned = ownedCard(feedbackId, attempt);
        if (owned.isEmpty()) {
            return false;
        }
        Feedback card = owned.get();
        card.complete(contentParser.toJson(presentation.score().content()), presentation.topicScore());
        replaceAxes(card, presentation.score().axisScores());
        return true;
    }

    /** 이 시도가 아직 유효할 때만 실패로 바꾼다. 다른 시도가 끝낸 카드는 건드리지 않는다 */
    @Transactional
    public void failCard(Long feedbackId, LocalDateTime attempt) {
        ownedCard(feedbackId, attempt).ifPresent(Feedback::fail);
    }

    /** 카드를 잠그고, 지금 진행 중인 시도가 이 시도일 때만 돌려준다 */
    private Optional<Feedback> ownedCard(Long feedbackId, LocalDateTime attempt) {
        return feedbackRepository.findByIdForUpdate(feedbackId)
                .filter(card -> card.isAttempt(attempt));
    }

    private void replaceAxes(Feedback card, Map<String, Integer> axisScores) {
        scoreAxisRepository.deleteByFeedbackId(card.getId());
        axisScores.forEach((code, value) -> scoreAxisRepository.save(ScoreAxis.create(card, code, value)));
    }

    // ===== 채점 입력 만들기 =====

    private MainQuestionInput topicInput(Long sessionId, Question main) {
        Map<Long, String> transcripts = new HashMap<>();
        for (Answer a : answerReadRepository.findBySessionId(sessionId)) {
            if (a.getQuestion() != null) {
                transcripts.put(a.getQuestion().getId(), a.getTranscript());
            }
        }

        List<TailInput> tails = questionReadRepository.findBySession_IdOrderBySeqAsc(sessionId).stream()
                .filter(q -> q.getParent() != null && q.getParent().getId().equals(main.getId()))
                .sorted(Comparator.comparing(Question::getSeq))
                .map(t -> new TailInput(
                        t.getStrategy() == null ? null : t.getStrategy().getLabel(),
                        t.getContent(),
                        transcripts.get(t.getId())))
                .toList();

        return new MainQuestionInput(main.getSeq(), main.getContent(), transcripts.get(main.getId()), tails);
    }

    private PresentationInput presentationInput(PtSession pt) {
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
}
