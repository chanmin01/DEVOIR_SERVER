package com.benly.pt.repository;

import com.benly.pt.entity.PtAudioReceipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PtAudioReceiptRepository extends JpaRepository<PtAudioReceipt, Long> {
    Optional<PtAudioReceipt> findByPtSession_IdAndRequestId(Long sessionId, String requestId);
}