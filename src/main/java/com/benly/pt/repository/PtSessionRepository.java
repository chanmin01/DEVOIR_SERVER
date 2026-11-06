package com.benly.pt.repository;

import com.benly.pt.entity.PtSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

import java.util.Optional;

public interface PtSessionRepository extends JpaRepository<PtSession, Long> {

    Optional<PtSession> findByCreationRequestId(String requestId);

    Optional<PtSession> findByIdAndSession_User_IdAndSession_DeletedAtIsNull(
            Long id,
            Long userId
    );
    // 공통 취소는 부모 Session UPDATE 후 같은 트랜잭션에서 PT 상세를 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PtSession p where p.id = :id")
    Optional<PtSession> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p.id from PtSession p
            where p.expiresAt is not null and p.expiresAt <= :now
              and p.session.deletedAt is null and p.retentionClosedAt is null
              and p.session.user.userType = com.benly.user.entity.UserType.GUEST
            order by p.id
            """)
    List<Long> findExpiredGuestIds(@Param("now") Instant now, Pageable pageable);
    @Query("select p.session.user.id from PtSession p where p.id = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);
}