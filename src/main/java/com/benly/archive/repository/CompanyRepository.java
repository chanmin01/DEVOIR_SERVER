package com.benly.archive.repository;

import com.benly.archive.dto.CompanyCandidate;
import com.benly.archive.entity.Company;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNormalizedName(String normalizedName);

    @Query("""
            select new com.benly.archive.dto.CompanyCandidate(
                c.id, c.name, c.source, c.normalizedName,
                (select count(q) from ArchiveQuestion q join q.archive a
                 where a.company = c
                   and a.deletedAt is null
                   and q.status = com.benly.archive.entity.QuestionStatus.VISIBLE))
            from Company c
            where c.normalizedName like concat(:key, '%')
            """)
    List<CompanyCandidate> searchByName(@Param("key") String key, Pageable pageable);
}
