package com.hwalro.simulation.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationResult;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class SimulationSchemaIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4").withDatabaseName("hwalro_simulation");

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(new UnpooledDataSource(
                MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/SimulationMapper.xml"));
        sqlSessionFactory = factory.getObject();
    }

    @AfterEach
    void restoreDensityThresholdSetting() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM density_threshold_settings");
            statement.executeUpdate("INSERT INTO density_threshold_settings (id, threshold_value, unit) "
                    + "VALUES (1, 3.500, 'PERSON_PER_M2')");
        }
    }

    @Test
    void simulationOptionsHasModelProfile() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet columns =
                        statement.executeQuery("SHOW COLUMNS FROM simulation_options LIKE 'model_profile'")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("Default")).isEqualTo("SFM_DEFAULT_V2");
        }
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet columns =
                        statement.executeQuery("SHOW COLUMNS FROM simulation_options LIKE 'routing_profile'")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("Default")).isEqualTo("HAZARD_RADIAL_EXP_V3");
        }
    }

    @Test
    void simulationOptionsHasInitialResponseTimeDistribution() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            for (String column : new String[] {"initial_response_time_mean", "initial_response_time_std_dev"}) {
                try (ResultSet columns =
                        statement.executeQuery("SHOW COLUMNS FROM simulation_options LIKE '" + column + "'")) {
                    assertThat(columns.next()).isTrue();
                    assertThat(columns.getString("Type")).isEqualTo("decimal(8,4)");
                    assertThat(columns.getString("Null")).isEqualTo("NO");
                    assertThat(columns.getString("Default")).isEqualTo("0.0000");
                }
            }
        }
    }

    @Test
    void densityThresholdSettingAllowsOnlyOnePositivePersonPerSquareMeterValue() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM density_threshold_settings");
            statement.executeUpdate("INSERT INTO density_threshold_settings (id, threshold_value, unit) "
                    + "VALUES (1, 3.500, 'PERSON_PER_M2')");

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO density_threshold_settings (id, threshold_value, unit) "
                                    + "VALUES (2, 3.500, 'PERSON_PER_M2')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                            "UPDATE density_threshold_settings SET threshold_value = 0 WHERE id = 1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                            "UPDATE density_threshold_settings SET unit = 'PEOPLE' WHERE id = 1"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void simulationsHasJsonFailureDetail() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery("SHOW COLUMNS FROM simulations LIKE 'failure_detail'")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("Type")).isEqualTo("json");
            assertThat(columns.getString("Null")).isEqualTo("YES");
        }
    }

    @Test
    void simulationResultsHasJsonTerminationDetail() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet columns =
                        statement.executeQuery("SHOW COLUMNS FROM simulation_results LIKE 'termination_detail'")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("Type")).isEqualTo("json");
            assertThat(columns.getString("Null")).isEqualTo("YES");
        }
    }

    @Test
    void simulationResultsHasJsonRecoveryDetail() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet columns =
                        statement.executeQuery("SHOW COLUMNS FROM simulation_results LIKE 'recovery_detail'")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("Type")).isEqualTo("json");
            assertThat(columns.getString("Null")).isEqualTo("YES");
        }
    }

    @Test
    void mapperPersistsAndReadsRecoveryDetailSeparatelyFromTerminationDetail() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO floor_plans (id, name, width, height) VALUES (951, 'recovery', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (952, 951, 7, 'recovery')");
            statement.executeUpdate(
                    "INSERT INTO layout_versions (id, layout_id, version, status) VALUES (953, 952, 1, '잠금')");
            statement.executeUpdate(
                    "INSERT INTO simulations (id, layout_version_id, created_by, title, status) VALUES (954, 953, 7, 'recovery', 'COMPLETED')");
        }

        String recovery =
                """
                {"schemaVersion":1,"scanCount":1,"eligibleGroupCount":1,"skippedEligibleGroupCount":0,"infeasibleScanCount":0,"recoveredGroupCount":1,"recoveredAgentCount":3,"recoveryTimeSeconds":0.5,"recoveredExitLabels":["1"],"recoveredExitIds":[501],"attemptedGroupSignatures":1,"events":[]}
                """
                        .strip();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            SimulationMapper mapper = session.getMapper(SimulationMapper.class);

            SimulationResult result = new SimulationResult();
            result.setSimulationId(954L);
            result.setEngineVersion("1.4.2+hwalro.2");
            result.setTerminationReason("ALL_EVACUATED");
            result.setFrameIntervalSeconds(BigDecimal.ONE);
            result.setRecoveryDetail(recovery);
            assertThat(mapper.insertSimulationResult(result)).isEqualTo(1);

            SimulationResult stored = mapper.findSimulationResult(954L);
            assertThat(stored.getRecoveryDetail()).isNotBlank();
            assertThat(new ObjectMapper().readTree(stored.getRecoveryDetail()))
                    .isEqualTo(new ObjectMapper().readTree(recovery));
            assertThat(stored.getTerminationDetail()).isNull();

            session.commit();
        }
    }

    @Test
    void mapperPersistsAndReadsTerminationDetail() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO floor_plans (id, name, width, height) VALUES (941, 'termination', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (942, 941, 7, 'termination')");
            statement.executeUpdate(
                    "INSERT INTO layout_versions (id, layout_id, version, status) VALUES (943, 942, 1, '잠금')");
            statement.executeUpdate(
                    "INSERT INTO simulations (id, layout_version_id, created_by, title, status) VALUES (944, 943, 7, 'termination', 'COMPLETED')");
        }

        String detail =
                """
                {"schemaVersion":1,"globalReason":"GLOBAL_STALLED","remainingPeople":1,"reasonCounts":{"ROUTE_FOLLOWING_STUCK":1},"representativeAgents":[1]}
                """
                        .strip();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            SimulationMapper mapper = session.getMapper(SimulationMapper.class);

            SimulationResult result = new SimulationResult();
            result.setSimulationId(944L);
            result.setEngineVersion("1.4.2+hwalro.2");
            result.setTerminationReason("STALLED");
            result.setFrameIntervalSeconds(BigDecimal.ONE);
            result.setTerminationDetail(detail);
            assertThat(mapper.insertSimulationResult(result)).isEqualTo(1);

            SimulationResult stored = mapper.findSimulationResult(944L);
            assertThat(stored.getTerminationDetail()).isNotBlank();
            assertThat(stored.getRecoveryDetail()).isNull();
            assertThat(new ObjectMapper().readTree(stored.getTerminationDetail()))
                    .isEqualTo(new ObjectMapper().readTree(detail));

            session.commit();
        }
    }

    @Test
    void densityThresholdDmlInitializesButDoesNotOverwriteExistingValue() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM density_threshold_settings");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/density-threshold-dml.sql"));
            assertThat(densityThreshold(statement)).isEqualByComparingTo("3.000");

            statement.executeUpdate("UPDATE density_threshold_settings SET threshold_value = 4.200 WHERE id = 1");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/density-threshold-dml.sql"));
            assertThat(densityThreshold(statement)).isEqualByComparingTo("4.200");
        }
    }

    @Test
    void mapperPersistsAndClearsFailureDetailAcrossExecutionTransitions() throws Exception {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO floor_plans (id, name, width, height) " + "VALUES (931, 'failure detail', 10, 10)");
            statement.executeUpdate("INSERT INTO layouts (id, floor_plan_id, created_by, title) "
                    + "VALUES (932, 931, 7, 'failure detail')");
            statement.executeUpdate(
                    "INSERT INTO layout_versions (id, layout_id, version, status) " + "VALUES (933, 932, 1, '잠금')");
            statement.executeUpdate("INSERT INTO simulations "
                    + "(id, layout_version_id, created_by, title, status) "
                    + "VALUES (934, 933, 7, 'failure detail', 'REQUESTED')");
            statement.executeUpdate("INSERT INTO simulations "
                    + "(id, layout_version_id, created_by, title, status, failure_message, failure_detail) "
                    + "VALUES (935, 933, 7, 'failure detail', 'RUNNING', 'stale', JSON_OBJECT('code', 'stale'))");
        }

        String detail =
                """
                {"schemaVersion":1,"code":"AGENT_ROUTE_UNREACHABLE","agentId":2,"currentPosition":{"x":1.5,"y":2.5},"recommendedPosition":null}
                """
                        .strip();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            SimulationMapper mapper = session.getMapper(SimulationMapper.class);

            assertThat(mapper.markExecutionFailed(934L, "route failed", detail)).isEqualTo(1);
            Simulation failed = mapper.findSimulationById(934L);
            assertThat(failed.getStatus()).isEqualTo("FAILED");
            assertThat(failed.getFailureMessage()).isEqualTo("route failed");
            assertThat(new ObjectMapper().readTree(failed.getFailureDetail()))
                    .isEqualTo(new ObjectMapper().readTree(detail));

            assertThat(mapper.requestExecution(934L)).isEqualTo(1);
            Simulation requested = mapper.findSimulationById(934L);
            assertThat(requested.getStatus()).isEqualTo("REQUESTED");
            assertThat(requested.getFailureMessage()).isNull();
            assertThat(requested.getFailureDetail()).isNull();

            assertThat(mapper.markExecutionCompleted(935L)).isEqualTo(1);
            Simulation completed = mapper.findSimulationById(935L);
            assertThat(completed.getStatus()).isEqualTo("COMPLETED");
            assertThat(completed.getFailureMessage()).isNull();
            assertThat(completed.getFailureDetail()).isNull();
            session.commit();
        }
    }

    @Test
    void parentSimulationCanBelongToDifferentLayoutVersion() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO floor_plans (id, name, width, height) VALUES (911, 'parent', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (912, 911, 7, 'parent')");
            statement.executeUpdate("INSERT INTO layout_versions (id, layout_id, version, status) VALUES "
                    + "(913, 912, 1, '잠금'), (914, 912, 2, '잠금')");
            statement.executeUpdate("INSERT INTO simulations (id, layout_version_id, created_by, title, status) "
                    + "VALUES (915, 913, 7, 'parent', 'DRAFT')");

            statement.executeUpdate("INSERT INTO simulations "
                    + "(id, layout_version_id, parent_simulation_id, created_by, title, status) "
                    + "VALUES (916, 914, 915, 7, 'parent', 'DRAFT')");
            Simulation parentDerived;
            try (SqlSession session = sqlSessionFactory.openSession()) {
                parentDerived = session.getMapper(SimulationMapper.class).findSimulationById(916L);
            }
            assertThat(parentDerived).isNotNull();
            assertThat(parentDerived.getParentSimulationId()).isEqualTo(915L);
        }
    }

    @Test
    void simulationStatusIsConstrained() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO floor_plans (id, name, width, height) VALUES (921, 'status', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (922, 921, 7, 'status')");
            statement.executeUpdate(
                    "INSERT INTO layout_versions (id, layout_id, version, status) VALUES (923, 922, 1, '잠금')");
            statement.executeUpdate("INSERT INTO simulations (id, layout_version_id, created_by, title, status) "
                    + "VALUES (924, 923, 7, 'status', 'CANCELLED')");

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO simulations (id, layout_version_id, created_by, title, status) "
                                    + "VALUES (925, 923, 7, 'status', 'UNKNOWN')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void simulationExitMustBelongToSameLayoutVersion() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO floor_plans (id, name, width, height) VALUES (901, 'test', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (902, 901, 7, 'test')");
            statement.executeUpdate("INSERT INTO layout_versions (id, layout_id, version, status) VALUES "
                    + "(903, 902, 1, '잠금'), (904, 902, 2, '잠금')");
            statement.executeUpdate("INSERT INTO layout_exits "
                    + "(id, layout_version_id, name, start_x, start_y, end_x, end_y) "
                    + "VALUES (905, 904, 'exit', 0, 0, 1, 0)");
            statement.executeUpdate("INSERT INTO simulations (id, layout_version_id, created_by, title, status) "
                    + "VALUES (906, 903, 7, 'test', 'DRAFT')");

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO simulation_exits (simulation_id, layout_exit_id, layout_version_id) "
                                    + "VALUES (906, 905, 903)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void isImprovementFlagIsComputedCorrectlyFromLineage() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO floor_plans (id, name, width, height) VALUES (931, 'lineage', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (932, 931, 7, 'lineage')");
            statement.executeUpdate("INSERT INTO layout_versions (id, layout_id, version, status) VALUES "
                    + "(933, 932, 1, '잠금'), (934, 932, 2, '잠금')");
            statement.executeUpdate("INSERT INTO simulations (id, layout_version_id, created_by, title, status) "
                    + "VALUES (935, 933, 7, 'baseline', 'COMPLETED')");
            statement.executeUpdate("INSERT INTO simulations "
                    + "(id, layout_version_id, parent_simulation_id, created_by, title, status) "
                    + "VALUES (936, 934, 935, 7, 'improvement', 'DRAFT')");
            statement.executeUpdate(
                    "INSERT INTO layout_searches (id, baseline_simulation_id, baseline_layout_version_id, "
                            + "planner_version, status, baseline_metrics, budget, requested_by) VALUES "
                            + "(937, 935, 933, 'v1', 'COMPLETED', '[]', '{\"verify\":false}', 7)");
            statement.executeUpdate("INSERT INTO layout_search_candidates (id, study_id, round_index, candidate_order, "
                    + "origin_finding_type, operator_type, status, change_set, rationale, prepared_simulation_id) VALUES "
                    + "(938, 937, 1, 1, 'BOTTLENECK', 'MOVE_OBJECT', 'QUEUED', '{}', '{}', 936)");

            try (SqlSession session = sqlSessionFactory.openSession()) {
                Simulation baseline = session.getMapper(SimulationMapper.class).findSimulationById(935L);
                Simulation improvement =
                        session.getMapper(SimulationMapper.class).findSimulationById(936L);
                assertThat(baseline.getIsImprovement()).isFalse();
                assertThat(improvement.getIsImprovement()).isTrue();
            }
        }
    }

    @Test
    void improvementSimulationCanBeDeletedWithoutDeletingItsLayoutSearch() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO floor_plans (id, name, width, height) VALUES (981, 'deletion', 10, 10)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (982, 981, 7, 'deletion')");
            statement.executeUpdate("INSERT INTO layout_versions (id, layout_id, version, status) VALUES "
                    + "(983, 982, 1, '잠금'), (984, 982, 2, '잠금')");
            statement.executeUpdate("INSERT INTO simulations (id, layout_version_id, created_by, title, status) "
                    + "VALUES (985, 983, 7, 'baseline', 'COMPLETED')");
            statement.executeUpdate("INSERT INTO simulations "
                    + "(id, layout_version_id, parent_simulation_id, created_by, title, status) "
                    + "VALUES (986, 984, 985, 7, 'improvement', 'COMPLETED')");
            statement.executeUpdate(
                    "INSERT INTO layout_searches (id, baseline_simulation_id, baseline_layout_version_id, "
                            + "planner_version, status, baseline_metrics, budget, requested_by) VALUES "
                            + "(987, 985, 983, 'v1', 'COMPLETED', '[]', '{\"verify\":false}', 7)");
            statement.executeUpdate("INSERT INTO layout_search_candidates "
                    + "(id, study_id, round_index, candidate_order, origin_finding_type, operator_type, status, "
                    + "change_set, rationale, prepared_simulation_id) VALUES "
                    + "(988, 987, 1, 1, 'BOTTLENECK', 'MOVE_OBJECT', 'EVALUATED', '{}', '{}', 986)");
        }

        try (SqlSession session = sqlSessionFactory.openSession()) {
            SimulationMapper mapper = session.getMapper(SimulationMapper.class);
            assertThat(mapper.countBlockingImprovementReferences(985L)).isPositive();
            assertThat(mapper.countBlockingImprovementReferences(986L)).isZero();
            assertThat(mapper.deleteSimulation(986L)).isEqualTo(1);
            session.commit();
        }

        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            try (ResultSet candidate = statement.executeQuery(
                    "SELECT prepared_simulation_id FROM layout_search_candidates WHERE id = 988")) {
                assertThat(candidate.next()).isTrue();
                assertThat(candidate.getObject("prepared_simulation_id")).isNull();
            }
            try (ResultSet search = statement.executeQuery("SELECT COUNT(*) FROM layout_searches WHERE id = 987")) {
                assertThat(search.next()).isTrue();
                assertThat(search.getInt(1)).isEqualTo(1);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static BigDecimal densityThreshold(Statement statement) throws SQLException {
        try (ResultSet resultSet =
                statement.executeQuery("SELECT threshold_value FROM density_threshold_settings WHERE id = 1")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBigDecimal("threshold_value");
        }
    }
}
