package com.benly.archive.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "company_aliases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "normalized_alias", nullable = false, length = 100)
    private String normalizedAlias;

    private CompanyAlias(Company company, String alias, String normalizedAlias) {
        this.company = company;
        this.alias = alias;
        this.normalizedAlias = normalizedAlias;
    }

    public static CompanyAlias of(Company company, String alias, String normalizedAlias) {
        return new CompanyAlias(company, alias, normalizedAlias);
    }
}
