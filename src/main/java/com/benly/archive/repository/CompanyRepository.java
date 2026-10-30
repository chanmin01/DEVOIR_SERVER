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
                c.id, c.name, c.source, c.normalizedName, count(q))
            from Company c
            left join InterviewArchive a on a.company = c and a.deletedAt is null
            left join ArchiveQuestion q on q.archive = a
                and q.status = com.benly.archive.entity.QuestionStatus.VISIBLE
            where c.normalizedName like concat(:key, '%')
            group by c.id, c.name, c.source, c.normalizedName
            having c.source = com.benly.archive.entity.CompanySource.DART or count(q) > 0
            order by case when c.normalizedName = :key then 0 else 1 end, count(q) desc, c.name asc
            """)
    List<CompanyCandidate> searchByName(@Param("key") String key, Pageable pageable);
}
