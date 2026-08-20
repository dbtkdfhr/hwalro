package com.hwalro.regulation.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.regulation.report.mapper.ReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ReportMapperStateTransitionIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("hwalro_regulation")
            .withUsername("hwalro_regulation")
            .withPassword("hwalro_regulation");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("jwt.secret", () -> "integration-test-secret-key-at-least-32-bytes");
    }

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearReports() {
        jdbcTemplate.update("DELETE FROM report_simulations");
        jdbcTemplate.update("DELETE FROM reports");
    }

    @Test
    void completesOnlyAiGenerationAndBlocksManualUpdateDuringGeneration() {
        jdbcTemplate.update(
                """
                INSERT INTO reports (id, author_id, title, content, status)
                VALUES (30, 7, 'AI 안전 검토 보고서', '{}', 'AI 작성 중')
                """);

        assertThat(reportMapper.updateReport(30L, "수동 변경", "{}", "작성 중")).isZero();
        assertThat(reportMapper.completeAiGeneration(30L, "생성 완료", "{}")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reports WHERE id = 30", String.class))
                .isEqualTo("초안");
        assertThat(reportMapper.completeAiGeneration(30L, "중복 완료", "{}")).isZero();
    }
}
