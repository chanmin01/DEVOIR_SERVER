package com.benly.archive.entity;

import com.benly.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "companies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "corp_code", length = 8)
    private String corpCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private CompanySource source;

    private Company(String corpCode, String name, String normalizedName, CompanySource source) {
        this.corpCode = corpCode;
        this.name = name;
        this.normalizedName = normalizedName;
        this.source = source;
    }

    public static Company ofDart(String corpCode, String name, String normalizedName) {
        return new Company(corpCode, name, normalizedName, CompanySource.DART);
    }

    public static Company ofUser(String name, String normalizedName) {
        return new Company(null, name, normalizedName, CompanySource.USER);
    }
}
