package com.benly.archive.service;

import com.benly.archive.dto.LikedQuestionListResponse;
import com.benly.archive.dto.MyArchiveListResponse;
import com.benly.archive.entity.ArchiveLike;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.ArchiveStatus;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveLikeRepository;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.global.exception.BusinessException;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ArchiveMyPageIntegrationTest {

    @Autowired private ArchiveMyPageService service;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private InterviewArchiveRepository archiveRepository;
    @Autowired private ArchiveQuestionRepository questionRepository;
    @Autowired private ArchiveLikeRepository likeRepository;

    private User user() {
        return userRepository.save(User.of("mypage-test-" + System.nanoTime(), "테스터"));
    }

    private InterviewArchive archive(User owner, int questionCount) {
        String key = "마이페이지회사" + System.nanoTime();
        Company company = companyRepository.save(Company.ofUser(key, key));
        InterviewArchive archive = archiveRepository.save(InterviewArchive.create(owner, company,
                CompanyType.SERVICE, InterviewStage.TECHNICAL, InterviewFormat.QA, "백엔드", YearMonth.of(2026, 10), null));
        for (int i = 1; i <= questionCount; i++) {
            questionRepository.save(ArchiveQuestion.of(archive, i, "마이페이지 테스트 질문 " + i));
        }
        return archive;
    }

    private ArchiveQuestion firstQuestion(InterviewArchive a) {
        return questionRepository.findByArchiveIdOrderBySeqAsc(a.getId()).get(0);
    }

    private void hide(InterviewArchive a) {
        a.addReport(1);
        assertThat(a.getStatus()).isEqualTo(ArchiveStatus.HIDDEN);
    }

    @Test
    @DisplayName("내 기록은 최신 등록순이고 숨김은 포함, 삭제는 제외한다")
    void myArchives() {
        User me = user();
        InterviewArchive first = archive(me, 2);
        InterviewArchive hidden = archive(me, 1);
        InterviewArchive deleted = archive(me, 3);
        archive(user(), 1);   // 남의 기록
        hide(hidden);
        deleted.softDelete();
        archiveRepository.flush();

        MyArchiveListResponse response = service.getMyArchives(me.getId(), null, null);

        assertThat(response.archives()).extracting(MyArchiveListResponse.Item::archiveId)
                .containsExactly(hidden.getId(), first.getId());
        assertThat(response.archives().get(0).status()).isEqualTo(ArchiveStatus.HIDDEN);
        assertThat(response.archives().get(1).questions()).hasSize(2);
        assertThat(response.totalQuestions()).isEqualTo(3);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("내 기록 질문에 내 하트 여부와 KST 등록 시각이 들어간다")
    void myArchivesLikedAndTime() {
        User me = user();
        InterviewArchive archive = archive(me, 2);
        likeRepository.save(ArchiveLike.of(me, firstQuestion(archive)));

        MyArchiveListResponse response = service.getMyArchives(me.getId(), null, null);

        MyArchiveListResponse.Item item = response.archives().get(0);
        assertThat(item.questions()).extracting(MyArchiveListResponse.Question::liked)
                .containsExactly(true, false);
        assertThat(item.createdAt()).endsWith("+09:00");
    }

    @Test
    @DisplayName("내 기록은 커서로 다음 페이지를 이어서 가져온다")
    void myArchivesPaging() {
        User me = user();
        InterviewArchive a1 = archive(me, 1);
        InterviewArchive a2 = archive(me, 1);
        InterviewArchive a3 = archive(me, 1);

        MyArchiveListResponse page1 = service.getMyArchives(me.getId(), null, 2);
        MyArchiveListResponse page2 = service.getMyArchives(me.getId(), page1.nextCursor(), 2);

        assertThat(page1.archives()).extracting(MyArchiveListResponse.Item::archiveId)
                .containsExactly(a3.getId(), a2.getId());
        assertThat(page1.hasNext()).isTrue();
        assertThat(page2.archives()).extracting(MyArchiveListResponse.Item::archiveId)
                .containsExactly(a1.getId());
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("하트한 질문은 최근 하트순이고 숨김, 삭제된 기록의 질문은 뺀다")
    void likedQuestions() {
        User me = user();
        User owner = user();
        InterviewArchive visible = archive(owner, 1);
        InterviewArchive hidden = archive(owner, 1);
        InterviewArchive deleted = archive(owner, 1);
        InterviewArchive mine = archive(me, 1);
        likeRepository.save(ArchiveLike.of(me, firstQuestion(visible)));
        likeRepository.save(ArchiveLike.of(me, firstQuestion(hidden)));
        likeRepository.save(ArchiveLike.of(me, firstQuestion(deleted)));
        likeRepository.save(ArchiveLike.of(me, firstQuestion(mine)));
        hide(hidden);
        deleted.softDelete();
        archiveRepository.flush();

        LikedQuestionListResponse response = service.getLikedQuestions(me.getId(), null, null);

        assertThat(response.questions()).extracting(LikedQuestionListResponse.Item::archiveId)
                .containsExactly(mine.getId(), visible.getId());
        assertThat(response.questions().get(0).mine()).isTrue();
        assertThat(response.questions().get(1).mine()).isFalse();
        assertThat(response.questions()).allMatch(LikedQuestionListResponse.Item::liked);
        assertThat(response.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("하트한 질문이 없으면 빈 목록과 0")
    void likedEmpty() {
        LikedQuestionListResponse response = service.getLikedQuestions(user().getId(), null, null);

        assertThat(response.questions()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("잘못된 커서와 size는 400")
    void invalidInput() {
        Long userId = user().getId();

        assertThatThrownBy(() -> service.getMyArchives(userId, "abc", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.INVALID_CURSOR);
        assertThatThrownBy(() -> service.getLikedQuestions(userId, null, 51))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ArchiveErrorCode.INVALID_PAGE_SIZE);
    }
}
