package com.benly.pt.repository;

import com.benly.pt.dto.PtCompanyOptionResponse;
import com.benly.pt.entity.PtTask;
import com.benly.pt.entity.PtTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PtTaskRepository
        extends JpaRepository<PtTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<PtTask> findFirstByCompany_IdAndStatusOrderByIdDesc(
            Long companyId,
            PtTaskStatus status
    );

    @Query("""
        select distinct new com.benly.pt.dto.PtCompanyOptionResponse(
            c.id,
            c.name,
            t
        )
        from PtTask t
        join t.company c
        where t.status = :status
          and not exists (
              select newer.id
              from PtTask newer
              where newer.company.id = c.id
                and newer.status = :status
                and newer.id > t.id
          )
        order by c.name
        """)
    List<PtCompanyOptionResponse> findAvailableCompanies(
            @Param("status") PtTaskStatus status
    );
}