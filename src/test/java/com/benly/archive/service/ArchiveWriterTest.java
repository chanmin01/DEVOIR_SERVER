package com.benly.archive.service;

import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.global.exception.BusinessException;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveWriterTest {

    private static final Long USER_ID = 1L;
    private static final String QUESTION_A = "Redis를 캐시로 쓰다가 장애가 나면 어떻게 되나요?";
    private static final String QUESTION_B = "왜 DB 락이 아니라 Redis 락을 고르셨나요?";

    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private InterviewArchiveRepository archiveRepository;
    @Mock
    private ArchiveQuestionRepository questionRepository;

    @InjectMocks
    private ArchiveWriter archiveWriter;

    private User user;
    private Company kakao;

    @BeforeEach
    void setUp() {
        user = User.of("12345", "찬민");
        kakao = Company.ofDart(null, "카카오", "카카오");
        ReflectionTestUtils.setField(kakao, "id", 3L);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    }

    private ArchiveWriter.NewArchive draft(List<String> questions) {
        return new ArchiveWriter.NewArchive(CompanyType.SERVICE, InterviewStage.TECHNICAL, InterviewFormat.QA,
                "백엔드", YearMonth.of(2026, 3), null, questions);
    }

    private InterviewArchive archive(long id) {
        InterviewArchive archive = InterviewArchive.create(user, kakao, CompanyType.SERVICE, InterviewStage.TECHNICAL,
                InterviewFormat.QA, "백엔드", YearMonth.of(2026, 3), null);
        ReflectionTestUtils.setField(archive, "id", id);
        return archive;
    }

    private void assertError(ArchiveWriter.NewArchive draft, ArchiveErrorCode expected) {
        assertThatThrownBy(() -> archiveWriter.save(USER_ID, new ResolvedCompany(kakao, false), draft))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
        verify(archiveRepository, never()).save(any());
    }

    @Test
    @DisplayName("오늘 19개에 2개를 더하면 20개를 넘어서 429")
    void dailyLimitExceeded() {
        given(questionRepository.countQuestionsSince(eq(USER_ID), any())).willReturn(19L);

        assertError(draft(List.of(QUESTION_A, QUESTION_B)), ArchiveErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("A 등록 후 B를 등록하고 10초 안에 A를 다시 보내면 409")
    void duplicateAfterAnotherArchive() {
        InterviewArchive archiveA = archive(41L);
        InterviewArchive archiveB = archive(42L);
        given(archiveRepository.findByUserIdAndCompanyIdAndCreatedAtAfter(eq(USER_ID), eq(3L), any()))
                .willReturn(List.of(archiveB, archiveA));
        given(questionRepository.findByArchiveIdOrderBySeqAsc(42L))
                .willReturn(List.of(ArchiveQuestion.of(archiveB, 1, QUESTION_B)));
        given(questionRepository.findByArchiveIdOrderBySeqAsc(41L))
                .willReturn(List.of(ArchiveQuestion.of(archiveA, 1, QUESTION_A)));

        assertError(draft(List.of(QUESTION_A)), ArchiveErrorCode.DUPLICATE_SUBMISSION);
    }

    @Test
    @DisplayName("사용자 잠금을 먼저 잡고 한도를 센 다음 저장한다")
    void lockBeforeCheckThenSave() {
        given(archiveRepository.findByUserIdAndCompanyIdAndCreatedAtAfter(eq(USER_ID), eq(3L), any()))
                .willReturn(List.of());
        given(companyRepository.getReferenceById(3L)).willReturn(kakao);
        given(archiveRepository.save(any(InterviewArchive.class))).willAnswer(inv -> inv.getArgument(0));
        given(questionRepository.save(any(ArchiveQuestion.class))).willAnswer(inv -> inv.getArgument(0));

        archiveWriter.save(USER_ID, new ResolvedCompany(kakao, false), draft(List.of(QUESTION_A, QUESTION_B)));

        InOrder order = inOrder(userRepository, questionRepository, archiveRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(questionRepository).countQuestionsSince(eq(USER_ID), any());
        order.verify(archiveRepository).save(any(InterviewArchive.class));
        verify(questionRepository, times(2)).save(any(ArchiveQuestion.class));
    }
}
