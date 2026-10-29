package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveReportRepository extends JpaRepository<ArchiveReport, Long> {

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);
}