package com.solo.equity;

import com.solo.equity.domain.Grant;
import com.solo.equity.repo.GrantRepository;
import com.solo.equity.service.BalanceService;
import com.solo.equity.service.GrantBalance;
import com.solo.equity.service.ScheduleService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application against a real PostgreSQL server:
 * <ul>
 *   <li>production DDL applied manually, JPA runs with {@code ddl-auto=validate}
 *       (every entity/column mapping must match the real schema &mdash; no H2 leniency);</li>
 *   <li>the "demo" seeder replays month-end grant + cliff + conditional batch +
 *       partial confirmed exercise + cancelled reservation against NUMERIC(18,4).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("demo")
class PostgresApplicationTest {

    static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) throws Exception {
        pg = EmbeddedPostgres.builder().start();
        String ddl = Files.readString(Path.of("..", "db", "init", "01_schema.sql"));
        try (var c = pg.getPostgresDatabase().getConnection();
             var st = c.createStatement()) {
            st.execute(ddl);
        }
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired BalanceService balances;
    @Autowired ScheduleService schedules;
    @Autowired GrantRepository grants;

    @Test
    void fullLifecycleStagesAgainstRealPostgres() {
        Grant g = grants.findAll().get(0);
        Long id = g.getId();

        // Cliff date: both requests were filed today; EX-002 is cancelled only later,
        // so as of today both reserve: 1200 vested / 1200 reserved / 0 exercisable.
        GrantBalance cliff = balances.balance(id, LocalDate.of(2026, 1, 31));
        assertThat(cliff.vested()).isEqualByComparingTo("1200");
        assertThat(cliff.unvested()).isEqualByComparingTo("3600");
        assertThat(cliff.pendingReserved()).isEqualByComparingTo("1200");
        assertThat(cliff.exercised()).isEqualByComparingTo("0");
        assertThat(cliff.exercisable()).isEqualByComparingTo("0");

        // Cancel day: 700 released; month-13 node not vested until Feb 28.
        GrantBalance cancelDay = balances.balance(id, LocalDate.of(2026, 2, 15));
        assertThat(cancelDay.vested()).isEqualByComparingTo("1200");
        assertThat(cancelDay.pendingReserved()).isEqualByComparingTo("500");
        assertThat(cancelDay.exercisable()).isEqualByComparingTo("700");

        // Confirmation day: Feb 28 node vested (1300), 500 now exercised.
        GrantBalance confirmDay = balances.balance(id, LocalDate.of(2026, 3, 10));
        assertThat(confirmDay.vested()).isEqualByComparingTo("1300");
        assertThat(confirmDay.exercised()).isEqualByComparingTo("500");
        assertThat(confirmDay.pendingReserved()).isEqualByComparingTo("0");
        assertThat(confirmDay.exercisable()).isEqualByComparingTo("800");

        // Conditional node 24 not marked: it does not vest even past its scheduled date.
        assertThat(balances.balance(id, LocalDate.of(2027, 1, 31)).vested())
                .isEqualByComparingTo("2300");

        // Even after the last scheduled month, the unmarked conditional node (100 shares)
        // never vests: 4700 vested, 100 still unvested. This is the PostgreSQL-validated
        // proof that one single "remaining balance" cannot describe the whole lifecycle.
        GrantBalance blocked = balances.balance(id, LocalDate.of(2029, 2, 1));
        assertThat(blocked.vested()).isEqualByComparingTo("4700");
        assertThat(blocked.unvested()).isEqualByComparingTo("100");
        assertThat(blocked.exercised()).isEqualByComparingTo("500");
        assertThat(blocked.exercisable()).isEqualByComparingTo("4200");

        // Once an administrator marks the condition satisfied, the last 100 vest.
        schedules.markConditionMet(id, 24, LocalDate.of(2029, 2, 2));
        GrantBalance end = balances.balance(id, LocalDate.of(2029, 2, 2));
        assertThat(end.vested()).isEqualByComparingTo("4800");
        assertThat(end.unvested()).isEqualByComparingTo("0");
        assertThat(end.exercised()).isEqualByComparingTo("500");
        assertThat(end.exercisable()).isEqualByComparingTo("4300");
    }
}
