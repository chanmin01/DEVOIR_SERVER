package com.benly.pt.repository;

import com.benly.pt.entity.PtQuestionTurn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PtQuestionTurnRepository extends JpaRepository<PtQuestionTurn, Long> {
    List<PtQuestionTurn> findByPtSession_IdOrderByOrdinalAsc(Long sessionId);

    Optional<PtQuestionTurn> findByPtSession_IdAndQuestion_Id(Long sessionId, Long questionId);

    Optional<PtQuestionTurn> findByPtSession_IdAndAnswerRequestId(Long sessionId, String requestId);
}