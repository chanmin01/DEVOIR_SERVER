package com.benly.archive.repository;

import com.benly.archive.entity.ArchiveLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveLikeRepository extends JpaRepository<ArchiveLike, Long> {

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);
}