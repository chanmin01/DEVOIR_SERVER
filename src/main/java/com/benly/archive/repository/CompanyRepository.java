package com.benly.archive.repository;

import com.benly.archive.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNormalizedName(String normalizedName);
}