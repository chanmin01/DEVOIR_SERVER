package com.benly.archive.repository;

import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewArchiveRepository extends JpaRepository<InterviewArchive, Long> {

    Optional<InterviewArchive> findByIdAndDeletedAtIsNull(Long id);

    List<InterviewArchive> findByUserIdAndCompanyIdAndCreatedAtAfter(Long userId, Long companyId, LocalDateTime after);

    @Query("""
            select a from InterviewArchive a
            join fetch a.company c
            where a.status = com.benly.archive.entity.ArchiveStatus.VISIBLE
              and a.deletedAt is null
              and (:companyId is null or c.id = :companyId)
              and (:companyType is null or a.companyType = :companyType)
              and (:stage is null or a.stage = :stage)
              and (:format is null or a.format = :format)
              and (:jobRole is null or a.jobRole = :jobRole)
              and (:cursorId is null or a.id < :cursorId)
            order by a.id desc
            """)
    List<InterviewArchive> findLatest(@Param("companyId") Long companyId,
                                      @Param("companyType") CompanyType companyType,
                                      @Param("stage") InterviewStage stage,
                                      @Param("format") InterviewFormat format,
                                      @Param("jobRole") String jobRole,
                                      @Param("cursorId") Long cursorId,
                                      Pageable pageable);

    @Query("""
            select a from InterviewArchive a
            join fetch a.company c
            where a.status = com.benly.archive.entity.ArchiveStatus.VISIBLE
              and a.deletedAt is null
              and (:companyId is null or c.id = :companyId)
              and (:companyType is null or a.companyType = :companyType)
              and (:stage is null or a.stage = :stage)
              and (:format is null or a.format = :format)
              and (:jobRole is null or a.jobRole = :jobRole)
              and (:cursorLikes is null
                   or a.likeCount < :cursorLikes
                   or (a.likeCount = :cursorLikes and a.id < :cursorId))
            order by a.likeCount desc, a.id desc
            """)
    List<InterviewArchive> findPopular(@Param("companyId") Long companyId,
                                       @Param("companyType") CompanyType companyType,
                                       @Param("stage") InterviewStage stage,
                                       @Param("format") InterviewFormat format,
                                       @Param("jobRole") String jobRole,
                                       @Param("cursorLikes") Integer cursorLikes,
                                       @Param("cursorId") Long cursorId,
                                       Pageable pageable);
}
