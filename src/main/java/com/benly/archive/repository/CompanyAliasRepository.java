package com.benly.archive.repository;

import com.benly.archive.entity.CompanyAlias;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, Long> {

    @EntityGraph(attributePaths = "company")
    Optional<CompanyAlias> findByNormalizedAlias(String normalizedAlias);
}