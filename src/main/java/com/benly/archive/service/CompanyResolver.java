package com.benly.archive.service;

import com.benly.archive.entity.Company;
import com.benly.archive.entity.CompanyAlias;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.CompanyAliasRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.support.CompanyNameNormalizer;
import com.benly.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompanyResolver {

    private static final int MIN_NAME_LENGTH = 2;

    private final CompanyRepository companyRepository;
    private final CompanyAliasRepository companyAliasRepository;

    public ResolvedCompany findOrCreate(String rawName) {
        String name = rawName.trim();
        String key = CompanyNameNormalizer.normalize(name);
        if (key.length() < MIN_NAME_LENGTH) {
            throw new BusinessException(ArchiveErrorCode.COMPANY_NAME_TOO_SHORT);
        }

        Optional<Company> existing = findExisting(key);
        if (existing.isPresent()) {
            return new ResolvedCompany(existing.get(), false);
        }

        try {
            Company created = companyRepository.saveAndFlush(Company.ofUser(name, key));
            return new ResolvedCompany(created, true);
        } catch (DataIntegrityViolationException e) {
            Company winner = companyRepository.findByNormalizedName(key).orElseThrow(() -> e);
            return new ResolvedCompany(winner, false);
        }
    }

    private Optional<Company> findExisting(String key) {
        return companyRepository.findByNormalizedName(key)
                .or(() -> companyAliasRepository.findByNormalizedAlias(key).map(CompanyAlias::getCompany));
    }
}
