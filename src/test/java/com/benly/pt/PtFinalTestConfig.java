package com.benly.pt;

import static org.mockito.Mockito.mock;

import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.pt.service.*;
import com.benly.question.client.WhisperClient;
import com.benly.session.service.SessionService;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.Map;

import javax.sql.DataSource;

@org.springframework.boot.test.context.TestConfiguration
@EnableTransactionManagement
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.benly")
@Import({
        PtSessionService.class,
        PtProgressService.class,
        PtContentCodec.class,
        PtInterviewTx.class,
        PtInterviewService.class,
        PtAudioService.class,
        PtGuestExpiryTx.class,
        PtGuestExpiryJob.class,
        PtSessionCancellationListener.class,
        SessionService.class
})
class PtFinalTestConfig {
    static class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-05T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    @Bean
    DataSource dataSource() {
        // 선택 사항: 전용 로컬 MySQL 테스트 컨테이너에서 같은 회귀 테스트를 실행한다.
        // 주소와 DB 이름을 고정해 운영 DB를 테스트 대상으로 지정할 수 없게 한다.
        String port = System.getenv("PT_TEST_MYSQL_PORT");
        if (port != null && !port.isBlank()) {
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) > 65535
                    || Integer.parseInt(port) < 1) {
                throw new IllegalArgumentException("PT_TEST_MYSQL_PORT가 올바르지 않습니다.");
            }
            String password = System.getenv("PT_TEST_MYSQL_PASSWORD");
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("전용 테스트 DB 비밀번호가 필요합니다.");
            }
            return new DriverManagerDataSource(
                    "jdbc:mysql://127.0.0.1:" + port
                            + "/pt_lock_test?allowPublicKeyRetrieval=true&useSSL=false"
                            + "&sessionVariables=transaction_isolation='REPEATABLE-READ'",
                    "root", password);
        }
        return new DriverManagerDataSource(
                "jdbc:h2:mem:ptfinal;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds, PtSqlProbe probe) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("com.benly");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(
                Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.time_zone", "UTC", "hibernate.session_factory.statement_inspector", probe));
        return factory;
    }

    @Bean
    PtSqlProbe sqlProbe() {
        return new PtSqlProbe();
    }

    static class PtSqlProbe implements org.hibernate.resource.jdbc.spi.StatementInspector {
        volatile java.util.function.Consumer<String> observer = sql -> {};

        @Override
        public String inspect(String sql) {
            observer.accept(sql);
            return sql;
        }
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    @Bean("ptClock")
    TestClock clock() {
        return new TestClock();
    }

    @Bean
    ObjectMapper mapper() {
        return new JsonMapper();
    }

    @Bean
    StaleTaskPolicy staleTaskPolicy() {
        return new StaleTaskPolicy(
                Duration.ofMinutes(10), Duration.ofMinutes(3), Duration.ofMinutes(15));
    }

    @Bean
    PtQuestionGenerator generator() {
        return mock(PtQuestionGenerator.class);
    }

    @Bean
    WhisperClient whisper() {
        return mock(WhisperClient.class);
    }
}