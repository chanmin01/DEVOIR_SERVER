package com.benly.archive.entity;

import com.benly.global.entity.BaseEntity;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Getter
@Table(name = "interview_archives")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewArchive extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false, length = 20)
    private CompanyType companyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private InterviewStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private InterviewFormat format;

    @Column(name = "job_role", length = 30)
    private String jobRole;

    @Column(name = "interviewed_at", nullable = false)
    private LocalDate interviewedAt;

    @Column(name = "memo", length = 500)
    private String memo;

    private InterviewArchive(User user, Company company, CompanyType companyType, InterviewStage stage,
                             InterviewFormat format, String jobRole, YearMonth interviewedMonth, String memo) {
        this.user = user;
        this.company = company;
        this.companyType = companyType;
        this.stage = stage;
        this.format = format;
        this.jobRole = jobRole;
        this.interviewedAt = interviewedMonth.atDay(1);
        this.memo = memo;
    }

    public static InterviewArchive create(User user, Company company, CompanyType companyType, InterviewStage stage,
                                          InterviewFormat format, String jobRole, YearMonth interviewedMonth, String memo) {
        return new InterviewArchive(user, company, companyType, stage, format, jobRole, interviewedMonth, memo);
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public YearMonth getInterviewedMonth() {
        return YearMonth.from(this.interviewedAt);
    }
}
