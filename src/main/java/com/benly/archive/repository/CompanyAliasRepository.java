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
                c.id, c.name, c.source,
                coalesce(max(case when al.normalizedAlias = :key then al.normalizedAlias end), min(al.normalizedAlias)),
                coalesce(max(case when al.normalizedAlias = :key then al.alias end), min(al.alias)),
                count(distinct q))
            from CompanyAlias al
            join al.company c
            left join InterviewArchive a on a.company = c and a.deletedAt is null
            left join ArchiveQuestion q on q.archive = a
                and q.status = com.benly.archive.entity.QuestionStatus.VISIBLE
            where al.normalizedAlias like concat(:key, '%')
            group by c.id, c.name, c.source
            having c.source = com.benly.archive.entity.CompanySource.DART or count(distinct q) > 0
            order by max(case when al.normalizedAlias = :key then 1 else 0 end) desc,
                     count(distinct q) desc, c.name asc
            """)
    List<CompanyCandidate> searchByAlias(@Param("key") String key, Pageable pageable);
}
