package com.benly.question.repository;

import com.benly.question.entity.Question;
import com.benly.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.benly.question.entity.FollowUpStrategy;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findFirstBySessionAndParentIsNullOrderBySeqAsc(Session session);

    int countByParent(Question parent); // 특정 메인의 꼬리 개수 파악

    int countByParentId(Long parentId); // 프록시 초기화 없이 ID만으로 꼬리 개수 파악

    List<Question> findByParentOrderBySeqAsc(Question parent); // 특정 메인의 꼬리 질문들 -> Claude에게 질문 시 맥락을 만들기 위해

    Optional<Question> findFirstBySessionAndParentIsNullAndSeqGreaterThanOrderBySeqAsc(Session session, Integer seq);

    // 답변이 없는 꼬리질문을 순서대로 조회 (JIT 생성이므로 존재한다면 항상 1순위 타겟)
    @Query("SELECT q FROM Question q WHERE q.session.id = :sessionId AND q.parent IS NOT NULL AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q) ORDER BY q.parent.seq ASC, q.seq ASC")
    List<Question> findUnansweredFollowUps(@Param("sessionId") Long sessionId);

    // 답변이 없는 메인 질문을 순서대로 조회
    @Query("SELECT q FROM Question q WHERE q.session.id = :sessionId AND q.parent IS NULL AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q) ORDER BY q.seq ASC")
    List<Question> findUnansweredMains(@Param("sessionId") Long sessionId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.session.id = :sessionId AND q.parent IS NULL AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q)")
    int countUnansweredMainsBySessionId(@Param("sessionId") Long sessionId);

    // 세션 전체의 메인 질문  (진행도 total)
    int countBySessionAndParentIsNull(Session session);

    // 답변이 없는 꼬리질문을 1건만 단건 조회
    @Query("SELECT q FROM Question q WHERE q.session.id = :sessionId AND q.parent IS NOT NULL AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q) ORDER BY q.parent.seq ASC, q.seq ASC LIMIT 1")
    Optional<Question> findFirstUnansweredFollowUpBySessionId(@Param("sessionId") Long sessionId);

    // 답변이 없는 메인 질문을 1건만 단건 조회
    @Query("SELECT q FROM Question q WHERE q.session.id = :sessionId AND q.parent IS NULL AND NOT EXISTS (SELECT a FROM Answer a WHERE a.question = q) ORDER BY q.seq ASC LIMIT 1")
    Optional<Question> findFirstUnansweredMainBySessionId(@Param("sessionId") Long sessionId);

    // 특정 메인에서 이미 사용한 꼬리질문 전략 (중복 전략 방지용)
    @Query("SELECT q.strategy FROM Question q " +
            "WHERE q.parent.id = :parentId AND q.strategy IS NOT NULL ORDER BY q.seq ASC")
    List<FollowUpStrategy> findStrategiesByParentId(@Param("parentId") Long parentId);
}
