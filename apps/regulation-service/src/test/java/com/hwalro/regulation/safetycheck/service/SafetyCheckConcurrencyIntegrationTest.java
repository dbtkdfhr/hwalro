package com.hwalro.regulation.safetycheck.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateResponse;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateUpdateRequest;
import com.hwalro.regulation.safetycheck.mapper.SafetyCheckMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SafetyCheckConcurrencyIntegrationTest {
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
    private SafetyCheckService safetyCheckService;

    @Autowired
    private SafetyCheckMapper safetyCheckMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearSafetyCheckData() {
        jdbcTemplate.update("DELETE FROM safety_inspection_items");
        jdbcTemplate.update("DELETE FROM safety_inspections");
        jdbcTemplate.update("DELETE FROM checklist_template_items");
        jdbcTemplate.update("DELETE FROM checklist_templates");
        jdbcTemplate.update("DELETE FROM inspection_areas");
    }

    @Test
    void concurrentTemplateUpdatesAllocateMonotonicVersionsAndOneActiveTemplate() throws Exception {
        insertArea();
        ChecklistTemplateUpdateRequest request = new ChecklistTemplateUpdateRequest(
                List.of(new ChecklistTemplateUpdateRequest.ItemInput("피난 통로 확인", "통로를 확보해야 함", "EVACUATION")));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ChecklistTemplateResponse> first = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return safetyCheckService.updateChecklistTemplate(900L, request);
            });
            Future<ChecklistTemplateResponse> second = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return safetyCheckService.updateChecklistTemplate(900L, request);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> allocatedVersions = List.of(
                    first.get(10, TimeUnit.SECONDS).version(),
                    second.get(10, TimeUnit.SECONDS).version());
            assertThat(allocatedVersions).containsExactlyInAnyOrder(1, 2);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM checklist_templates WHERE inspection_area_id = 900", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM checklist_templates WHERE inspection_area_id = 900 AND status = 'ACTIVE'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT version FROM checklist_templates WHERE inspection_area_id = 900 ORDER BY version",
                        Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    void draftUpdateCannotModifyInspectionAfterConcurrentCompletion() throws Exception {
        insertArea();
        insertInspection();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch bothReadDraft = new CountDownLatch(2);
        CountDownLatch startUpdates = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> completion = executor.submit(() -> transaction.execute(status -> {
                assertThat(safetyCheckMapper.findInspectionHeader(930L).status())
                        .isEqualTo("DRAFT");
                bothReadDraft.countDown();
                await(startUpdates);
                assertThat(safetyCheckMapper.updateInspectionItem(
                                930L, 940L, "PASS", "완료 요청", null, null, LocalDateTime.now()))
                        .isEqualTo(1);
                return safetyCheckMapper.updateInspection(930L, "COMPLETED", "완료 요청", LocalDateTime.now());
            }));
            Future<Integer> draftSave = executor.submit(() -> transaction.execute(status -> {
                assertThat(safetyCheckMapper.findInspectionHeader(930L).status())
                        .isEqualTo("DRAFT");
                bothReadDraft.countDown();
                await(startUpdates);
                sleep(300);
                return safetyCheckMapper.updateInspectionItem(
                        930L, 940L, "FAIL", "뒤늦은 임시 저장", null, null, LocalDateTime.now());
            }));

            assertThat(bothReadDraft.await(5, TimeUnit.SECONDS)).isTrue();
            startUpdates.countDown();

            assertThat(completion.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(draftSave.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM safety_inspections WHERE id = 930", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT result FROM safety_inspection_items WHERE id = 940", String.class))
                .isEqualTo("PASS");
    }

    @Test
    void concurrentGetOrCreateCreatesSingleOpenDraft() throws Exception {
        insertArea();
        jdbcTemplate.update("INSERT INTO checklist_templates "
                + "(id, inspection_area_id, version, status) VALUES (910, 900, 1, 'ACTIVE')");
        JwtUser inspector = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Long> createdIds;
        try {
            Future<Long> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                return safetyCheckService
                        .getOrCreateOpenInspection(900L, inspector)
                        .id();
            });
            Future<Long> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                return safetyCheckService
                        .getOrCreateOpenInspection(900L, inspector)
                        .id();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            createdIds = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(createdIds.get(0)).isEqualTo(createdIds.get(1));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM safety_inspections WHERE inspection_area_id = 900", Integer.class))
                .isEqualTo(1);
    }

    private void insertArea() {
        jdbcTemplate.update("INSERT INTO inspection_areas (id, name, active) VALUES (900, '통합 테스트 구역', TRUE)");
    }

    private void insertInspection() {
        jdbcTemplate.update(
                "INSERT INTO checklist_templates (id, inspection_area_id, version, status) VALUES (910, 900, 1, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO checklist_template_items "
                + "(id, checklist_template_id, title, category, display_order) "
                + "VALUES (920, 910, '피난 통로 확인', 'EVACUATION', 1)");
        jdbcTemplate.update("INSERT INTO safety_inspections "
                + "(id, inspection_area_id, checklist_template_id, inspector_id, status) "
                + "VALUES (930, 900, 910, 3, 'DRAFT')");
        jdbcTemplate.update("INSERT INTO safety_inspection_items "
                + "(id, safety_inspection_id, template_item_id, title, category, display_order, result) "
                + "VALUES (940, 930, 920, '피난 통로 확인', 'EVACUATION', 1, 'PENDING')");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while waiting for concurrent test step.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test was interrupted.", exception);
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test was interrupted.", exception);
        }
    }
}
