package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ArchiveQuestionRepository extends JpaRepository<ArchiveQuestion, Long> {

    List<ArchiveQuestion> findByArchiveIdOrderBySeqAsc(Long archiveId);

    // 삭제한 등록 건의 질문도 센다. 지우고 다시 올리는 우회를 막기 위해
    @Query("""
            select count(q) from ArchiveQuestion q
            where q.archive.user.id = :userId
              and q.archive.createdAt >= :from
            """)
    long countQuestionsSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}