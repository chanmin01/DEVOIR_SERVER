package com.benly.pt.entity;

import com.benly.archive.entity.Company;
import com.benly.global.entity.BaseTimeEntity;
import com.benly.session.entity.CompanyType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Enumerated(EnumType.STRING)
    @Column(
            name = "task_type",
            nullable = false,
            length = 30,
            updatable = false
    )
    private PtTaskType taskType =
            PtTaskType.SERVICE_IMPROVEMENT;

    @Column(
            name = "target_job_role",
            length = 100,
            updatable = false
    )
    private String targetJobRole;

    @Getter(AccessLevel.NONE)
    @Column(
            name = "allowed_tracks",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String allowedTracksValue =
            "SECURITY,UI_UX,AI,DATA";

    @Getter(AccessLevel.NONE)
    @Column(
            name = "preparation_minutes_options",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String preparationMinutesOptionsValue =
            "15,20";

    @Column(
            name = "presentation_seconds",
            nullable = false,
            updatable = false
    )
    private int presentationSeconds = 300;

    public List<PtTrack> getAllowedTracks() {
        if (allowedTracksValue.isBlank()) {
            return List.of();
        }

        return Arrays.stream(allowedTracksValue.split(","))
                .map(String::strip)
                .map(PtTrack::valueOf)
                .toList();
    }

    public List<Integer> getPreparationMinutesOptions() {
        return Arrays.stream(
                        preparationMinutesOptionsValue.split(",")
                )
                .map(String::strip)
                .map(Integer::valueOf)
                .toList();
    }

    public boolean requiresTrackSelection() {
        return !getAllowedTracks().isEmpty();
    }

    public boolean allowsPreparationMinutes(Integer minutes) {
        return minutes != null
                && getPreparationMinutesOptions()
                .contains(minutes);
    }

    public static PtTask approvedExercise(
            Company company,
            CompanyType companyType,
            String code,
            String contentJson,
            PtTaskType taskType,
            String targetJobRole,
            List<PtTrack> tracks,
            List<Integer> preparationMinutes,
            int presentationSeconds
    ) {
        Objects.requireNonNull(taskType);
        Objects.requireNonNull(tracks);
        Objects.requireNonNull(preparationMinutes);

        if (tracks.size() > PtTrack.values().length
                || tracks.stream().anyMatch(Objects::isNull)
                || preparationMinutes.isEmpty()
                || preparationMinutes.size() > 10
                || preparationMinutes.stream().anyMatch(
                minute -> minute == null
                        || minute < 1
                        || minute > 120
        )
                || presentationSeconds < 60
                || presentationSeconds > 1800
                || (
                targetJobRole != null
                        && targetJobRole.length() > 100
        )) {
            throw new IllegalArgumentException(
                    "PT 과제 진행 설정을 확인해주세요."
            );
        }

        PtTask task = approvedSample(
                company,
                companyType,
                code,
                contentJson
        );

        task.taskType = taskType;

        task.targetJobRole =
                targetJobRole == null
                        || targetJobRole.isBlank()
                        ? null
                        : targetJobRole.strip();

        task.allowedTracksValue = tracks.stream()
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(","));

        task.preparationMinutesOptionsValue =
                preparationMinutes.stream()
                        .distinct()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

        task.presentationSeconds = presentationSeconds;

        return task;
    }

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