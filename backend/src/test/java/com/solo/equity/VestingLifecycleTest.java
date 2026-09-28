package com.solo.equity;

import com.solo.equity.domain.Grant;
import com.solo.equity.repo.GrantRepository;
import com.solo.equity.repo.VestingNodeRepository;
import com.solo.equity.service.BalanceService;
import com.solo.equity.service.BusinessRuleException;
import com.solo.equity.service.ExerciseService;
import com.solo.equity.service.GrantBalance;
import com.solo.equity.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end stage-by-stage reconciliation on H2 with the same JPA mappings used
 * against PostgreSQL in production.
 *
 * Fictional plan: grant dated 2025-01-31 (month-end), 4,800 shares, 12-month cliff,
 * 48 monthly nodes (100 shares per month), node 24 (2027-01-31) conditional.
 */
@SpringBootTest
class VestingLifecycleTest {

    @Autowired ScheduleService schedules;
    @Autowired BalanceService balances;
    @Autowired ExerciseService exercises;
    @Autowired GrantRepository grants;
    @Autowired VestingNodeRepository nodes;

    private static final LocalDate GRANT_DATE = LocalDate.of(2025, 1, 31);
    private static final LocalDate CLIFF_DATE = LocalDate.of(2026, 1, 31);

    private Long newStandardGrant(List<Integer> conditionalMonths) {
        Grant g = schedules.createGrant("OPT-2025-A", "Alex Founder", GRANT_DATE,
                new BigDecimal("4800"), 12, 48, "Fictional Plan 2025", conditionalMonths);
        return g.getId();
    }

    @Test
    void monthEndGrantClampsVestDatesAndBuildsExactCliffSchedule() {
        Long id = newStandardGrant(List.of());

        // 37 nodes: one cliff batch (12 slices) + months 13..48 (36 monthly nodes).
        var timeline = nodes.findByGrantIdOrderBySeqNoAsc(id);
        assertThat(timeline).hasSize(37);

        var cliff = timeline.get(0);
        assertThat(cliff.getSeqNo()).isEqualTo(12);
        assertThat(cliff.getScheduledQuantity()).isEqualByComparingTo("1200");
        // Jan 31 + 12 months stays at month end; Jan 31 + 1 month clamps to Feb 28.
        assertThat(cliff.getVestDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(timeline.get(1).getVestDate()).isEqualTo(LocalDate.of(2026, 2, 28));

        BigDecimal nodeSum = timeline.stream()
                .map(n -> n.getScheduledQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(nodeSum).isEqualByComparingTo("4800");
    }

    @Test
    void eachStageReportedSeparatelyBeforeAndAfterCliff() {
        Long id = newStandardGrant(List.of());

        GrantBalance atGrant = balances.balance(id, GRANT_DATE);
        assertThat(atGrant.vested()).isEqualByComparingTo("0");
        assertThat(atGrant.unvested()).isEqualByComparingTo("4800");
        assertThat(atGrant.exercisable()).isEqualByComparingTo("0");

        // One day before the cliff: nothing vests early.
        assertThat(balances.balance(id, CLIFF_DATE.minusDays(1)).vested())
                .isEqualByComparingTo("0");

        // On the cliff date the whole 12-month batch lands at once.
        GrantBalance atCliff = balances.balance(id, CLIFF_DATE);
        assertThat(atCliff.vested()).isEqualByComparingTo("1200");
        assertThat(atCliff.unvested()).isEqualByComparingTo("3600");
        assertThat(atCliff.exercised()).isEqualByComparingTo("0");
        assertThat(atCliff.pendingReserved()).isEqualByComparingTo("0");
        assertThat(atCliff.exercisable()).isEqualByComparingTo("1200");

        // One month later: 13 monthly slices vested (Feb 2026 node clamped to the 28th).
        GrantBalance month13 = balances.balance(id, LocalDate.of(2026, 2, 28));
        assertThat(month13.vested()).isEqualByComparingTo("1300");
        assertThat(month13.unvested()).isEqualByComparingTo("3500");
    }

    @Test
    void conditionalBatchWaitsForManualMarkEvenAfterScheduledDate() {
        Long id = newStandardGrant(List.of(24)); // node at 2027-01-31 conditional

        // 2027-01-31: months 13..23 vested (11 x 100) + cliff 1200 = 2300;
        // the conditional node 24 stays UNVESTED despite its scheduled date passing.
        GrantBalance beforeMark = balances.balance(id, LocalDate.of(2027, 1, 31));
        assertThat(beforeMark.vested()).isEqualByComparingTo("2300");
        assertThat(beforeMark.unvested()).isEqualByComparingTo("2500");

        // Condition satisfied only on 2027-03-15; day before, it is still unvested.
        schedules.markConditionMet(id, 24, LocalDate.of(2027, 3, 15));
        assertThat(balances.balance(id, LocalDate.of(2027, 3, 14)).vested())
                .isEqualByComparingTo("2400"); // months up to 25 (Feb-28 node) but not 24

        // On the mark date node 24 vests: 1200 + months 13..25 (13 x 100) = 2500.
        assertThat(balances.balance(id, LocalDate.of(2027, 3, 15)).vested())
                .isEqualByComparingTo("2500");
    }

    @Test
    void partialExercisesReservationOverExerciseRejectionCancelReleaseAndConfirmation() {
        Long id = newStandardGrant(List.of());

        // At the cliff 1200 are exercisable. Reserve 500 with a partial request.
        var ex1 = exercises.request(id, "EX-001", new BigDecimal("500"), CLIFF_DATE);
        GrantBalance afterFirst = balances.balance(id, CLIFF_DATE);
        assertThat(afterFirst.vested()).isEqualByComparingTo("1200");
        assertThat(afterFirst.pendingReserved()).isEqualByComparingTo("500");
        assertThat(afterFirst.exercised()).isEqualByComparingTo("0");
        assertThat(afterFirst.exercisable()).isEqualByComparingTo("700");

        // Asking for more than the remaining pool is rejected and reserves nothing.
        assertThatThrownBy(() -> exercises.request(id, "EX-BAD",
                new BigDecimal("701"), CLIFF_DATE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds exercisable");

        // A second request that exactly exhausts the pool is accepted.
        var ex2 = exercises.request(id, "EX-002", new BigDecimal("700"), CLIFF_DATE);
        assertThat(balances.balance(id, CLIFF_DATE).exercisable()).isEqualByComparingTo("0");

        // Cancel EX-002 on 2026-02-15 (vested is still 1200: month-13 node vests Feb 28).
        exercises.cancel(ex2.getId(), LocalDate.of(2026, 2, 15));
        GrantBalance afterCancel = balances.balance(id, LocalDate.of(2026, 2, 15));
        assertThat(afterCancel.pendingReserved()).isEqualByComparingTo("500");
        assertThat(afterCancel.exercisable()).isEqualByComparingTo("700");

        // Historical query one day BEFORE the cancellation reconstructs the reservation.
        GrantBalance beforeCancel = balances.balance(id, LocalDate.of(2026, 2, 14));
        assertThat(beforeCancel.pendingReserved()).isEqualByComparingTo("1200");
        assertThat(beforeCancel.exercisable()).isEqualByComparingTo("0");

        // Confirm EX-001 on 2026-03-10: by then month 13 has vested (1300 total).
        exercises.confirm(ex1.getId(), LocalDate.of(2026, 3, 10));
        GrantBalance afterConfirm = balances.balance(id, LocalDate.of(2026, 3, 10));
        assertThat(afterConfirm.vested()).isEqualByComparingTo("1300");
        assertThat(afterConfirm.exercised()).isEqualByComparingTo("500");
        assertThat(afterConfirm.pendingReserved()).isEqualByComparingTo("0");
        assertThat(afterConfirm.exercisable()).isEqualByComparingTo("800");

        // Historical query before confirmation: EX-001 was still PENDING then.
        GrantBalance beforeConfirm = balances.balance(id, LocalDate.of(2026, 3, 9));
        assertThat(beforeConfirm.exercised()).isEqualByComparingTo("0");
        assertThat(beforeConfirm.pendingReserved()).isEqualByComparingTo("500");

        // Fully vested end state: 4800 vested, 500 exercised, rest exercisable.
        GrantBalance end = balances.balance(id, LocalDate.of(2029, 2, 1));
        assertThat(end.vested()).isEqualByComparingTo("4800");
        assertThat(end.unvested()).isEqualByComparingTo("0");
        assertThat(end.exercised()).isEqualByComparingTo("500");
        assertThat(end.exercisable()).isEqualByComparingTo("4300");
    }

    @Test
    void nonDivisibleQuantityIsExactLastNodeAbsorbsRoundingRemainder() {
        Grant g = schedules.createGrant("OPT-ODD", "Bo Lee", LocalDate.of(2025, 3, 31),
                new BigDecimal("1000"), 0, 48, "Fictional Plan 2025", List.of());
        var timeline = nodes.findByGrantIdOrderBySeqNoAsc(g.getId());

        BigDecimal sum = timeline.stream()
                .map(n -> n.getScheduledQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 1000/48 = 20.8333 monthly; the nodes must still sum to exactly 1000.0000.
        assertThat(sum).isEqualByComparingTo("1000");
        assertThat(timeline.get(timeline.size() - 1).getScheduledQuantity())
                .isEqualByComparingTo("20.8349"); // 47 x 20.8333 = 979.1651; remainder
    }
}
