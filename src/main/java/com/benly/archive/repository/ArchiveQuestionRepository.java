package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveQuestionRepository extends JpaRepository<ArchiveQuestion, Long> {

    List<ArchiveQuestion> findByArchiveIdOrderBySeqAsc(Long archiveId);
}