package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArchiveLikeRepository extends JpaRepository<ArchiveLike, Long> {

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    @Query("""
            select l.question.id from ArchiveLike l
            where l.user.id = :userId and l.question.id in :questionIds
            """)
    List<Long> findLikedQuestionIds(@Param("userId") Long userId,
                                    @Param("questionIds") Collection<Long> questionIds);
}
