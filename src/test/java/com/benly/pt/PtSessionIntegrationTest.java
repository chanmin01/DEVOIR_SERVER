package com.benly.pt;

import com.benly.archive.entity.Company;
import com.benly.archive.repository.CompanyRepository;
import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.repository.*;
import com.benly.pt.service.*;
import com.benly.session.entity.*;
import com.benly.session.repository.SessionRepository;
import com.benly.session.service.SessionService;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(PtSessionIntegrationTest.Config.class)
class PtSessionIntegrationTest {

    static class MutableClock extends Clock {

        Instant now =
                Instant.parse("2026-11-03T05:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackages = "com.benly")
    @Import({
            PtSessionService.class,
            PtProgressService.class,
            PtSessionCancellationListener.class,
            PtOptionService.class,
            PtContentCodec.class,
            SessionService.class
    })
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:ptpr1;MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        LocalContainerEntityManagerFactoryBean
        entityManagerFactory(DataSource dataSource) {
            var factory =
                    new LocalContainerEntityManagerFactoryBean();

            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.benly");
            factory.setJpaVendorAdapter(
                    new HibernateJpaVendorAdapter()
            );

            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.jdbc.time_zone", "UTC"
            ));

            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(
                EntityManagerFactory factory
        ) {
            return new JpaTransactionManager(factory);
        }

        @Bean("ptClock")
        MutableClock clock() {
            return new MutableClock();
        }

        @Bean
        StaleTaskPolicy staleTaskPolicy() {
            return new StaleTaskPolicy(
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(3),
                    Duration.ofMinutes(15)
            );
        }

        @Bean
        ObjectMapper mapper() {
            return new JsonMapper();
        }
    }

    @Autowired
    PtSessionService service;

    @Autowired
    PtOptionService options;

    @Autowired
    PtSessionRepository details;

    @Autowired
    PtTaskRepository tasks;

    @Autowired
    SessionRepository sessions;

    @Autowired
    UserRepository users;

    @Autowired
    CompanyRepository companies;

    @Autowired
    PtContentCodec codec;

    @Autowired
    MutableClock clock;

    @Autowired
    JpaTransactionManager manager;

    @Autowired
    SessionService existingSessionService;

    Long companyId;
    Long userId;
    Long otherId;
    Long guestId;
    PtTaskContent content;

    @BeforeEach
    void setup() {
        clock.now =
                Instant.parse("2026-11-03T05:00:00Z");

        new TransactionTemplate(manager).execute(status -> {
            details.deleteAll();
            sessions.deleteAll();
            tasks.deleteAll();
            companies.deleteAll();
            users.deleteAll();

            var company = companies.saveAndFlush(
                    Company.ofUser("기업은행", "기업은행")
            );

            companyId = company.getId();

            userId = users.saveAndFlush(
                    User.of("pt-member", "회원")
            ).getId();

            otherId = users.saveAndFlush(
                    User.of("pt-other", "다른회원")
            ).getId();

            guestId = users.saveAndFlush(
                    User.createGuest()
            ).getId();

            content = new PtTaskContent(
                    "가상 서비스 고도화",
                    "가상 금융 상담 서비스",
                    List.of("입력", "동의", "상담"),
                    "연습용 문제 상황",
                    List.of("재무자료"),
                    List.of("기존 절차 연결"),
                    List.of("3개월은 가정"),
                    Map.of(
                            PtTrack.SECURITY, "접근권한",
                            PtTrack.UI_UX, "이해도",
                            PtTrack.AI, "설명",
                            PtTrack.DATA, "자료 품질"
                    ),
                    List.of("5분 발표")
            );

            tasks.saveAndFlush(PtTask.approvedSample(
                    company,
                    CompanyType.FINANCE_IT,
                    "SAMPLE",
                    codec.content(content)
            ));

            return null;
        });
    }

    PtSessionCreateRequest request(
            UUID requestId,
            int minutes
    ) {
        return new PtSessionCreateRequest(
                requestId,
                companyId,
                minutes,
                "백엔드"
        );
    }

    @Test
    void createsExistingSessionAndOneToOnePtDetail() {
        var response = service.create(
                userId,
                request(UUID.randomUUID(), 20)
        );

        assertEquals(
                SessionStatus.IN_PROGRESS,
                response.status()
        );

        assertEquals(
                PtPhase.PREPARING,
                response.phase()
        );

        assertEquals(
                Instant.parse("2026-11-03T05:20:00Z"),
                response.preparationDeadline()
        );

        assertEquals(
                companyId,
                response.task().companyId()
        );

        assertNull(response.expiresAt());

        assertTrue(
                sessions.existsById(response.sessionId())
        );

        assertTrue(
                details.existsById(response.sessionId())
        );

        var parent = sessions.findById(response.sessionId())
                .orElseThrow();

        assertEquals(
                "FINANCE_IT",
                parent.getCompanyType()
        );

        assertEquals(
                "백엔드",
                parent.getJobTitle()
        );
    }

    @Test
    void linkedTaskAndDeadlineSurviveReload() {
        var first = service.create(
                userId,
                request(UUID.randomUUID(), 15)
        );

        clock.now = clock.now.plusSeconds(120);

        var reloaded = service.get(
                userId,
                first.sessionId()
        );

        assertEquals(
                first.preparationDeadline(),
                reloaded.preparationDeadline()
        );

        assertEquals(
                first.task(),
                reloaded.task()
        );

        assertEquals(
                clock.instant(),
                reloaded.serverTime()
        );
    }

    @Test
    void sameRequestReturnsSameSessionAndChangedPayloadConflicts() {
        UUID requestId = UUID.randomUUID();

        var first = service.create(
                userId,
                request(requestId, 15)
        );

        clock.now = clock.now.plusSeconds(20);

        assertEquals(
                first.sessionId(),
                service.create(
                        userId,
                        request(requestId, 15)
                ).sessionId()
        );

        assertEquals(1, sessions.count());

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        userId,
                        request(requestId, 20)
                )
        );

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        otherId,
                        request(requestId, 15)
                )
        );
    }

    @Test
    void concurrentCreationIsIdempotent()
            throws Exception {
        UUID requestId = UUID.randomUUID();

        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Long> action = () -> {
                start.await();

                return service.create(
                        userId,
                        request(requestId, 20)
                ).sessionId();
            };

            var first = pool.submit(action);
            var second = pool.submit(action);

            start.countDown();

            assertEquals(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertEquals(1, sessions.count());
        }
    }

    @Test
    void validatesCompanyAndMinutesAndOwnership() {
        assertThrows(
                BusinessException.class,
                () -> service.create(
                        userId,
                        request(UUID.randomUUID(), 16)
                )
        );

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        userId,
                        new PtSessionCreateRequest(
                                UUID.randomUUID(),
                                Long.MAX_VALUE,
                                20,
                                null
                        )
                )
        );

        var first = service.create(
                userId,
                request(UUID.randomUUID(), 20)
        );

        assertThrows(
                BusinessException.class,
                () -> service.get(
                        otherId,
                        first.sessionId()
                )
        );
    }

    @Test
    void companiesComeFromExistingCompanyEntitiesWithApprovedTasks() {
        assertEquals(
                List.of(companyId),
                options.getOptions().companies().stream()
                        .map(PtCompanyOptionResponse::companyId)
                        .toList()
        );

        assertEquals(
                "기업은행",
                options.getOptions().companies()
                        .getFirst().companyName()
        );

        assertEquals(
                6,
                options.getOptions().totalQuestionCount()
        );

        tasks.deleteAll();

        assertTrue(
                options.getOptions().companies().isEmpty()
        );

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        userId,
                        request(UUID.randomUUID(), 20)
                )
        );
    }

    @Test
    void guestHasTemporaryRetentionAndExpiryIsEnforced() {
        var first = service.create(
                guestId,
                request(UUID.randomUUID(), 15)
        );

        assertEquals(
                clock.instant().plusSeconds(86400),
                first.expiresAt()
        );

        clock.now = first.expiresAt();

        assertThrows(
                BusinessException.class,
                () -> service.get(
                        guestId,
                        first.sessionId()
                )
        );
    }

    @Test
    void existingCancelIsReflectedInPtResponse() {
        var first = service.create(
                userId,
                request(UUID.randomUUID(), 20)
        );

        existingSessionService.cancelSession(
                userId,
                first.sessionId()
        );

        var canceled = service.get(
                userId,
                first.sessionId()
        );

        assertEquals(
                SessionStatus.CANCELED,
                canceled.status()
        );

        assertEquals(
                PtPhase.CANCELED,
                canceled.phase()
        );
    }

    @Test
    void existingNormalSessionCreationStillWorks() {
        var request =
                new com.benly.session.dto.SessionCreateRequest(
                        "SERVICE",
                        "TECHNICAL",
                        "카카오",
                        "백엔드",
                        null,
                        null
                );

        var normal = existingSessionService.createSession(
                userId,
                request
        );

        assertEquals(
                "GENERATING",
                normal.status()
        );

        assertFalse(
                details.existsById(normal.sessionId())
        );
    }

    @Test
    void retiredTaskRemainsReadableAndNewSessionsUseNewTask() {
        UUID originalRequest = UUID.randomUUID();

        var first = service.create(
                userId,
                request(originalRequest, 20)
        );

        new TransactionTemplate(manager).execute(status -> {
            var oldTask = tasks.findById(
                    first.task().taskId()
            ).orElseThrow();

            oldTask.retire();

            var company = companies.findById(companyId)
                    .orElseThrow();

            var nextContent = new PtTaskContent(
                    "새 과제",
                    content.serviceDescription(),
                    content.currentFlow(),
                    content.problemSituation(),
                    content.availableData(),
                    content.constraints(),
                    content.exerciseAssumptions(),
                    content.trackGuidance(),
                    content.presentationRequirements()
            );

            tasks.saveAndFlush(PtTask.approvedSample(
                    company,
                    CompanyType.FINANCE_IT,
                    "SAMPLE_V2",
                    codec.content(nextContent)
            ));

            return null;
        });

        var next = service.create(
                userId,
                request(UUID.randomUUID(), 20)
        );

        assertNotEquals(
                first.task().taskId(),
                next.task().taskId()
        );

        assertEquals(
                "새 과제",
                next.task().content().title()
        );

        assertEquals(
                first.task(),
                service.get(
                        userId,
                        first.sessionId()
                ).task()
        );

        assertEquals(
                first.sessionId(),
                service.create(
                        userId,
                        request(originalRequest, 20)
                ).sessionId()
        );
    }

    @Test
    void twoSessionsReferenceTheSameOriginalTask() {
        var first = service.create(
                userId,
                request(UUID.randomUUID(), 15)
        );

        var second = service.create(
                otherId,
                request(UUID.randomUUID(), 20)
        );

        assertNotEquals(
                first.sessionId(),
                second.sessionId()
        );

        assertEquals(
                first.task().taskId(),
                second.task().taskId()
        );

        assertEquals(1, tasks.count());
        assertEquals(2, details.count());

        new TransactionTemplate(manager).execute(status -> {
            var one = details.findById(first.sessionId())
                    .orElseThrow();

            var two = details.findById(second.sessionId())
                    .orElseThrow();

            assertSame(
                    one.getTask(),
                    two.getTask()
            );

            return null;
        });
    }

    @Test
    void withdrawnUserCannotCreateOrRead() {
        var first = service.create(
                userId,
                request(UUID.randomUUID(), 20)
        );

        new TransactionTemplate(manager).execute(status -> {
            users.findById(userId)
                    .orElseThrow()
                    .withdraw();

            return null;
        });

        assertThrows(
                BusinessException.class,
                () -> service.get(
                        userId,
                        first.sessionId()
                )
        );

        assertThrows(
                BusinessException.class,
                () -> service.create(
                        userId,
                        request(UUID.randomUUID(), 20)
                )
        );
    }
}