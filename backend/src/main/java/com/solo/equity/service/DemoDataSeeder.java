package com.solo.equity.service;

import com.solo.equity.domain.Grant;
import com.solo.equity.repo.GrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Demo data for the reconciliation scenario required by the administrator:
 * month-end grant date, 12-month cliff, conditional batch at month 24,
 * a partial exercise that is later confirmed, and a second PENDING request
 * that is cancelled and releases its reservation.
 *
 * Activated only with the "demo" profile. Fictional plan data only.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ScheduleService schedules;
    private final ExerciseService exercises;
    private final GrantRepository grants;

    public DemoDataSeeder(ScheduleService schedules, ExerciseService exercises,
                          GrantRepository grants) {
        this.schedules = schedules;
        this.exercises = exercises;
        this.grants = grants;
    }

    @Override
    public void run(String... args) {
        if (grants.count() > 0) {
            return;
        }
        Grant g = schedules.createGrant(
                "OPT-2025-A", "Alex Founder",
                LocalDate.of(2025, 1, 31),     // month-end grant date
                new BigDecimal("4800"),
                12,                              // 12-month cliff
                48,                              // 48 monthly slices (100 shares each)
                "Founders Fictional Plan 2025",
                List.of(24));                    // month-24 batch needs a manual condition mark

        LocalDate cliff = LocalDate.of(2026, 1, 31);

        // Partial exercise on the cliff date: 500 of the 1200 vested shares, confirmed later.
        var ex1 = exercises.request(g.getId(), "EX-001", new BigDecimal("500"), cliff);
        exercises.confirm(ex1.getId(), LocalDate.of(2026, 3, 10));

        // Second reservation of 700 (exactly exhausting the pool), cancelled unconfirmed:
        // as of 2026-02-15 the 700 shares are released back to exercisable.
        var ex2 = exercises.request(g.getId(), "EX-002", new BigDecimal("700"), cliff);
        exercises.cancel(ex2.getId(), LocalDate.of(2026, 2, 15));

        log.info("Demo data seeded: grant {} with cliff, conditional month 24, "
                + "confirmed partial exercise and cancelled request", g.getId());
    }
}
