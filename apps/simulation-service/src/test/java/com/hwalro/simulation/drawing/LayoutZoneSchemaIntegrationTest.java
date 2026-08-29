package com.hwalro.simulation.drawing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class LayoutZoneSchemaIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4").withDatabaseName("hwalro_simulation");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }
    }

    @BeforeEach
    void resetFixture() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM layout_zone_members");
            statement.executeUpdate("DELETE FROM layout_placement_exclusions");
            statement.executeUpdate("DELETE FROM layout_zones");
            statement.executeUpdate("DELETE FROM walls");
            statement.executeUpdate("DELETE FROM pillars");
            statement.executeUpdate("DELETE FROM fabrics");
            statement.executeUpdate("DELETE FROM layout_exits");
            statement.executeUpdate("DELETE FROM layout_versions");
            statement.executeUpdate("DELETE FROM layouts");
            statement.executeUpdate("DELETE FROM floor_plans");

            statement.executeUpdate("INSERT INTO floor_plans (id, name, width, height) VALUES (801, 'zone', 100, 100)");
            statement.executeUpdate(
                    "INSERT INTO layouts (id, floor_plan_id, created_by, title) VALUES (802, 801, 7, 'zone')");
            statement.executeUpdate("INSERT INTO layout_versions (id, layout_id, version, status) VALUES "
                    + "(803, 802, 1, '초안'), (804, 802, 2, '초안')");
            statement.executeUpdate("INSERT INTO layout_exits "
                    + "(id, layout_version_id, name, start_x, start_y, end_x, end_y) VALUES "
                    + "(805, 803, 'exit-a', 0, 0, 1, 0), (806, 804, 'exit-other-version', 0, 0, 1, 0)");
            statement.executeUpdate("INSERT INTO walls "
                    + "(id, layout_version_id, name, start_x, start_y, end_x, end_y) VALUES "
                    + "(811, 803, 'wall-a', 1, 1, 2, 2)");
            statement.executeUpdate("INSERT INTO pillars "
                    + "(id, layout_version_id, name, start_x, start_y, end_x, end_y) VALUES "
                    + "(812, 803, 'pillar-a', 1, 1, 2, 2)");
            statement.executeUpdate("INSERT INTO fabrics "
                    + "(id, layout_version_id, name, start_x, start_y, end_x, end_y) VALUES "
                    + "(807, 803, 'fabric-a', 1, 1, 2, 2), (808, 803, 'fabric-b', 3, 3, 4, 4)");
            statement.executeUpdate("INSERT INTO layout_zones "
                    + "(id, layout_version_id, name, zone_type, x, y, width, height) VALUES "
                    + "(809, 803, 'zone-a', 'WORK', 0, 0, 10, 10), (810, 803, 'zone-b', 'WORK', 20, 20, 10, 10)");
        }
    }

    @Test
    void oneStructureBelongsToAtMostOneZone() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, fabric_id) VALUES (803, 809, 807)");

            assertThatThrownBy(
                            () -> statement.executeUpdate(
                                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, fabric_id) VALUES (803, 810, 807)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void oneWallBelongsToAtMostOneZone() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, wall_id) VALUES (803, 809, 811)");

            assertThatThrownBy(
                            () -> statement.executeUpdate(
                                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, wall_id) VALUES (803, 810, 811)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void memberRequiresExactlyOneElementColumn() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO layout_zone_members (layout_version_id, zone_id, wall_id, pillar_id) "
                                    + "VALUES (803, 809, 811, 812)"))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO layout_zone_members (layout_version_id, zone_id) VALUES (803, 809)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void memberCannotReferenceWallFromAnotherLayoutVersion() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO walls "
                    + "(id, layout_version_id, name, start_x, start_y, end_x, end_y) VALUES "
                    + "(813, 804, 'wall-other-version', 1, 1, 2, 2)");

            assertThatThrownBy(
                            () -> statement.executeUpdate(
                                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, wall_id) VALUES (803, 809, 813)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void deletingWallRemovesMembershipButKeepsZone() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, wall_id) VALUES (803, 809, 811)");

            statement.executeUpdate("DELETE FROM walls WHERE id = 811");

            assertThat(count(statement, "SELECT COUNT(*) FROM layout_zone_members WHERE wall_id = 811"))
                    .isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM layout_zones WHERE id = 809"))
                    .isEqualTo(1);
        }
    }

    @Test
    void zoneCannotReferenceExitFromAnotherLayoutVersion() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE layout_zones SET default_exit_id = 805 WHERE id = 809");

            assertThatThrownBy(() ->
                            statement.executeUpdate("UPDATE layout_zones SET default_exit_id = 806 WHERE id = 809"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void movementPolicyAcceptsOnlyThreeLevels() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE fabrics SET movement_policy = 'FREE' WHERE id = 807");
            statement.executeUpdate("UPDATE fabrics SET movement_policy = 'WITHIN_ZONE' WHERE id = 807");
            statement.executeUpdate("UPDATE fabrics SET movement_policy = 'FIXED' WHERE id = 807");

            assertThatThrownBy(() ->
                            statement.executeUpdate("UPDATE fabrics SET movement_policy = 'TELEPORT' WHERE id = 807"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void deletingZoneRemovesMembershipButKeepsStructure() throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO layout_zone_members (layout_version_id, zone_id, fabric_id) VALUES (803, 809, 807)");

            statement.executeUpdate("DELETE FROM layout_zones WHERE id = 809");

            assertThat(count(statement, "SELECT COUNT(*) FROM layout_zone_members WHERE zone_id = 809"))
                    .isZero();
            assertThat(count(statement, "SELECT COUNT(*) FROM fabrics WHERE id = 807"))
                    .isEqualTo(1);
        }
    }

    private static int count(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
