package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.dto.FeedbackContent.ImprovedAnswer;
import com.benly.feedback.dto.FeedbackContent.TailContent;
import com.benly.feedback.dto.FeedbackReportResponse.Card;
import com.benly.feedback.entity.Feedback;
import com.benly.question.entity.Answer;
import com.benly.question.entity.FollowUpStrategy;
import com.benly.question.entity.Question;
import com.benly.question.entity.QuestionSourceType;
import com.benly.session.entity.Session;
import com.benly.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeedbackReportAssemblerTest {

    private FeedbackReportAssembler assembler;
    private FeedbackContentParser parser;
    private Session session;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        parser = new FeedbackContentParser(mapper);
        assembler = new FeedbackReportAssembler(parser);
        session = Session.create(mock(User.class), "IT", "PERSONALITY", "백엔드", "카카오");
    }

    private Question main(long id, int seq) {
        Question q = Question.createMain(session, seq, "메인질문" + seq, QuestionSourceType.CLAUDE);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private Question tail(long id, Question parent, int seq, String content) {
        Question q = Question.createFollowUp(session, parent, seq, content, FollowUpStrategy.WHY, QuestionSourceType.CLAUDE);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private Answer answer(Question q, String transcript) {
        return Answer.createText(q, transcript);
    }

    @Test
    @DisplayName("꼬리 피드백이 질문 순서(seq)대로 정확히 매칭된다")
    void tailsMatchedByOrder() {
        // given
        Question m = main(100L, 1);
        Question t1 = tail(101L, m, 2, "꼬리1");
        Question t2 = tail(102L, m, 3, "꼬리2");
        FeedbackContent content = new FeedbackContent(
                "메인good", "메인weak", "메인next", "메인축",
                new ImprovedAnswer("before", "after"),
                List.of(
                        new TailContent("꼬리1good", "꼬리1weak", "꼬리1next", "꼬리1축", "꼬리1개선"),
                        new TailContent("꼬리2good", "꼬리2weak", "꼬리2next", "꼬리2축", "꼬리2개선")
                ));
        Feedback fb = Feedback.create(m, parser.toJson(content), 80);

        // when
        List<Card> cards = assembler.assembleCards(
                List.of(fb),
                List.of(m, t2, t1),                          // ← 역순으로 전달
                List.of(answer(t2, "꼬리2답"), answer(t1, "꼬리1답"), answer(m, "메인답")));

        // then
        assertThat(cards).hasSize(1);
        Card card = cards.get(0);
        assertThat(card.tails()).hasSize(2);
        assertThat(card.tails().get(0).question()).isEqualTo("꼬리1");
        assertThat(card.tails().get(0).good()).isEqualTo("꼬리1good");
        assertThat(card.tails().get(0).answer()).isEqualTo("꼬리1답");
        assertThat(card.tails().get(1).question()).isEqualTo("꼬리2");
        assertThat(card.tails().get(1).good()).isEqualTo("꼬리2good");
    }

    @Test
    @DisplayName("꼬리 피드백이 질문보다 적으면 남는 꼬리는 null 피드백으로 채워진다")
    void fewerFeedbackThanQuestions() {
        // given
        Question m = main(100L, 1);
        Question t1 = tail(101L, m, 2, "꼬리1");
        Question t2 = tail(102L, m, 3, "꼬리2");
        FeedbackContent content = new FeedbackContent(
                "g", "w", "n", "a", null,
                List.of(new TailContent("꼬리1good", "w", "n", "a", "i")));
        Feedback fb = Feedback.create(m, parser.toJson(content), 70);

        // when
        List<Card> cards = assembler.assembleCards(
                List.of(fb), List.of(m, t1, t2), List.of());

        // then
        assertThat(cards.get(0).tails()).hasSize(2);
        assertThat(cards.get(0).tails().get(0).good()).isEqualTo("꼬리1good");
        assertThat(cards.get(0).tails().get(1).good()).isNull();
        assertThat(cards.get(0).tails().get(1).question()).isEqualTo("꼬리2");
    }

    @Test
    @DisplayName("content가 없으면 꼬리 피드백은 null이지만 질문/답변은 유지된다")
    void nullContent() {
        // given
        Question m = main(100L, 1);
        Question t1 = tail(101L, m, 2, "꼬리1");
        Feedback fb = Feedback.create(m, null, 0);

        // when
        List<Card> cards = assembler.assembleCards(
                List.of(fb), List.of(m, t1), List.of(answer(t1, "꼬리1답")));

        // then
        assertThat(cards.get(0).tails()).hasSize(1);
        assertThat(cards.get(0).tails().get(0).good()).isNull();
        assertThat(cards.get(0).tails().get(0).answer()).isEqualTo("꼬리1답");
    }
}