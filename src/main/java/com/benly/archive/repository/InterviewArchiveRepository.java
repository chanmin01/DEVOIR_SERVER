package com.benly.archive.repository;

import com.benly.archive.entity.InterviewArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewArchiveRepository extends JpaRepository<InterviewArchive, Long> {

    Optional<InterviewArchive> findByIdAndDeletedAtIsNull(Long id);

    List<InterviewArchive> findByUserIdAndCompanyIdAndCreatedAtAfter(Long userId, Long companyId, LocalDateTime after);
}
