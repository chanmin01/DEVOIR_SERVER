package com.benly.archive.repository;

import com.benly.archive.dto.CompanyCandidate;
import com.benly.archive.entity.CompanyAlias;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, Long> {

    @EntityGraph(attributePaths = "company")
    Optional<CompanyAlias> findByNormalizedAlias(String normalizedAlias);

    @Query("""
            select new com.benly.archive.dto.CompanyCandidate(
                c.id, c.name, c.source, al.normalizedAlias, al.alias,
                (select count(q) from ArchiveQuestion q join q.archive a
                 where a.company = c
                   and a.deletedAt is null
                   and q.status = com.benly.archive.entity.QuestionStatus.VISIBLE))
            from CompanyAlias al join al.company c
            where al.normalizedAlias like concat(:key, '%')
            """)
    List<CompanyCandidate> searchByAlias(@Param("key") String key, Pageable pageable);
}
