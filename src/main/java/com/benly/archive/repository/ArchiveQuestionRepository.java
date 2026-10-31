package com.benly.archive.repository;

import com.benly.archive.dto.CompanyQuestionCount;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.session.entity.CompanyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ArchiveQuestionRepository extends JpaRepository<ArchiveQuestion, Long> {

    List<ArchiveQuestion> findByArchiveIdOrderBySeqAsc(Long archiveId);

    @Query("""
            select count(q) from ArchiveQuestion q
            where q.archive.user.id = :userId
              and q.archive.createdAt >= :from
            """)
    long countQuestionsSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("""
            select q from ArchiveQuestion q
            where q.archive.id in :archiveIds
            order by q.archive.id, q.seq
            """)
    List<ArchiveQuestion> findByArchiveIds(@Param("archiveIds") Collection<Long> archiveIds);

    @Query("""
            select new com.benly.archive.dto.CompanyQuestionCount(
                c.id, c.name, count(q), max(a.interviewedAt))
            from ArchiveQuestion q
            join q.archive a
            join a.company c
            where a.status = com.benly.archive.entity.ArchiveStatus.VISIBLE
              and a.deletedAt is null
              and (:companyType is null or a.companyType = :companyType)
            group by c.id, c.name
            having (:cursorCount is null
                    or count(q) < :cursorCount
                    or (count(q) = :cursorCount and c.id < :cursorId))
            order by count(q) desc, c.id desc
            """)
    List<CompanyQuestionCount> countByCompany(@Param("companyType") CompanyType companyType,
                                              @Param("cursorCount") Long cursorCount,
                                              @Param("cursorId") Long cursorId,
                                              Pageable pageable);
}
