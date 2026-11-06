package com.benly.pt.entity;

import com.benly.archive.entity.Company;
import com.benly.global.entity.BaseTimeEntity;
import com.benly.session.entity.CompanyType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "pt_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pt_task_code",
                columnNames = {"company_id", "code"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            updatable = false
    )
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "company_type",
            nullable = false,
            length = 20,
            updatable = false
    )
    private CompanyType companyType;

    @Column(
            nullable = false,
            length = 100,
            updatable = false
    )
    private String code;

    @Column(
            name = "task_version",
            nullable = false,
            updatable = false
    )
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PtTaskStatus status;

    @Column(
            name = "content_json",
            nullable = false,
            columnDefinition = "MEDIUMTEXT",
            updatable = false
    )
    private String contentJson;

    public void retire() {
        this.status = PtTaskStatus.RETIRED;
    }

    public static PtTask approvedSample(
            Company company,
            CompanyType type,
            String code,
            String contentJson
    ) {
        PtTask task = new PtTask();

        task.company = company;
        task.companyType = type;
        task.code = code;
        task.version = 1;
        task.status = PtTaskStatus.APPROVED;
        task.contentJson = contentJson;

        return task;
    }
}