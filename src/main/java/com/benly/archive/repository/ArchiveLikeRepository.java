package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveLike;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying(flushAutomatically = true)
    @Query("delete from ArchiveLike l where l.user.id = :userId and l.question.id = :questionId")
    int deleteByUserIdAndQuestionId(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Query("""
            select l from ArchiveLike l
            join fetch l.question q
            join fetch q.archive a
            join fetch a.company
            where l.user.id = :userId
              and a.status = com.benly.archive.entity.ArchiveStatus.VISIBLE
              and a.deletedAt is null
              and (:cursorId is null or l.id < :cursorId)
            order by l.id desc
            """)
    List<ArchiveLike> findVisibleLikes(@Param("userId") Long userId,
                                       @Param("cursorId") Long cursorId,
                                       Pageable pageable);

    @Query("""
            select count(l) from ArchiveLike l
            where l.user.id = :userId
              and l.question.archive.status = com.benly.archive.entity.ArchiveStatus.VISIBLE
              and l.question.archive.deletedAt is null
            """)
    long countVisibleLikes(@Param("userId") Long userId);

}
