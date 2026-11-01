package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArchiveReportRepository extends JpaRepository<ArchiveReport, Long> {

    boolean existsByUserIdAndArchiveId(Long userId, Long archiveId);

    @Query("""
            select r.archive.id from ArchiveReport r
            where r.user.id = :userId and r.archive.id in :archiveIds
            """)
    List<Long> findReportedArchiveIds(@Param("userId") Long userId,
                                      @Param("archiveIds") Collection<Long> archiveIds);
}
