package com.solo.equity;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test against a <b>real PostgreSQL server</b> with the production DDL:
 * proves the NUMERIC(18,4) columns store the BigDecimal share counts exactly and
 * the identity/date columns used by the JPA mappings are accepted as written.
 *
 * Business-stage reconciliation itself is covered end-to-end through JPA in
 * {@link VestingLifecycleTest}; here we verify persistence-level fidelity.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresNumericSmokeTest {

    private EmbeddedPostgres pg;
    private String jdbcUrl;

    @BeforeAll
    void startDatabase() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        jdbcUrl = pg.getJdbcUrl("postgres", "postgres");
        String ddl = Files.readString(Path.of("..", "db", "init", "01_schema.sql"));
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement()) {
            // The production init script runs once on an empty database.
            st.execute(ddl);
        }
    }

    @AfterAll
    void stopDatabase() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void numericColumnsPreserveFourDigitShareCountsAndTimelineMath() throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            // Insert a month-end grant of 4800 shares.
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO grants
                      (grant_name, grantee, grant_date, total_quantity, cliff_months, vesting_months, plan_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                ps.setString(1, "OPT-PG");
                ps.setString(2, "Dana Roe");
                ps.setObject(3, LocalDate.of(2025, 1, 31));
                ps.setBigDecimal(4, new BigDecimal("4800.0000"));
                ps.setInt(5, 12);
                ps.setInt(6, 48);
                ps.setString(7, "Fictional Plan 2025");
                ps.executeUpdate();
            }

            // Cliff node: 1200 shares at 2026-01-31; month-13 node: 100 at 2026-02-28 (month-end clamp).
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO vesting_nodes
                      (grant_id, seq_no, vest_date, scheduled_quantity, cliff_node, conditional_flag)
                    VALUES (1, ?, ?, ?, ?, ?)""")) {
                ps.setInt(1, 12);
                ps.setObject(2, LocalDate.of(2026, 1, 31));
                ps.setBigDecimal(3, new BigDecimal("1200.0000"));
                ps.setBoolean(4, true);
                ps.setBoolean(5, false);
                ps.addBatch();

                ps.setInt(1, 13);
                ps.setObject(2, LocalDate.of(2026, 2, 28));
                ps.setBigDecimal(3, new BigDecimal("100.0000"));
                ps.setBoolean(4, false);
                ps.setBoolean(5, false);
                ps.addBatch();
                ps.executeBatch();
            }

            // A pending reservation of 500 shares at the cliff date.
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO exercise_requests
                      (grant_id, request_no, quantity, request_date, status)
                    VALUES (1, 'EX-PG', 500.0000, '2026-01-31', 'PENDING')""")) {
                ps.executeUpdate();
            }

            // As-of 2026-02-28: vested 1300, pending 500, exercisable = vested - pending = 800.
            String query = """
                    WITH vested AS (
                        SELECT COALESCE(SUM(scheduled_quantity), 0) AS v
                        FROM vesting_nodes
                        WHERE conditional_flag = FALSE OR condition_met_at IS NOT NULL
                          AND GREATEST(vest_date, COALESCE(condition_met_at, vest_date)) <= ?
                    ), pending AS (
                        SELECT COALESCE(SUM(quantity), 0) AS p
                        FROM exercise_requests
                        WHERE request_date <= ? AND status = 'PENDING'
                    ), exercised AS (
                        SELECT COALESCE(SUM(quantity), 0) AS e
                        FROM exercise_requests
                        WHERE request_date <= ? AND status = 'CONFIRMED'
                          AND confirmed_date <= ?
                    )
                    SELECT vested.v, pending.p, exercised.e,
                           vested.v - pending.p - exercised.e AS exercisable
                    FROM vested, pending, exercised""";
            try (PreparedStatement ps = c.prepareStatement(query)) {
                LocalDate asOf = LocalDate.of(2026, 2, 28);
                ps.setObject(1, asOf);
                ps.setObject(2, asOf);
                ps.setObject(3, asOf);
                ps.setObject(4, asOf);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("1300");
                    assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("500");
                    assertThat(rs.getBigDecimal(3)).isEqualByComparingTo("0");
                    assertThat(rs.getBigDecimal(4)).isEqualByComparingTo("800");
                }
            }

            // The column type is NUMERIC(18,4), not float: exact scale is preserved.
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT total_quantity::text FROM grants WHERE grant_name = 'OPT-PG'""")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("4800.0000");
            }
        }
    }
}
