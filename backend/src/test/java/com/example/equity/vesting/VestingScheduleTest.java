package com.example.equity.vesting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VestingScheduleTest {

    @Test
    void monthEndGrantClampsEveryNodeToMonthEnd() {
        // 2026-01-31 授予 4800 股，12 个月、无悬崖
        List<VestingSchedule.PlannedNode> nodes = VestingSchedule.plan(
                LocalDate.parse("2026-01-31"), new BigDecimal("4800"), 0, 12);

        assertThat(nodes).hasSize(12);
        assertThat(nodes.get(0).nodeDate()).isEqualTo(LocalDate.parse("2026-02-28")); // 非闰年 2 月
        assertThat(nodes.get(1).nodeDate()).isEqualTo(LocalDate.parse("2026-03-31"));
        assertThat(nodes.get(2).nodeDate()).isEqualTo(LocalDate.parse("2026-04-30"));
        assertThat(nodes.get(11).nodeDate()).isEqualTo(LocalDate.parse("2027-01-31"));
        assertThat(nodes).allSatisfy(n -> {
            assertThat(n.nodeDate()).isEqualTo(n.nodeDate().withDayOfMonth(n.nodeDate().lengthOfMonth()));
            assertThat(n.shares()).isEqualByComparingTo("400");
        });
    }

    @Test
    void cliffAccumulatesAtCliffNodeThenVestsMonthly() {
        // 月底授予 4800 股，12 个月归属、12 个月悬崖（一次性归属）
        List<VestingSchedule.PlannedNode> nodes = VestingSchedule.plan(
                LocalDate.parse("2026-01-31"), new BigDecimal("4800"), 12, 12);

        assertThat(nodes.get(11).shares()).isEqualByComparingTo("4800");
        assertThat(nodes.subList(0, 11)).allSatisfy(n ->
                assertThat(n.shares()).isEqualByComparingTo("0"));
    }

    @Test
    void roundingDifferenceIsAbsorbedByLastNode() {
        // 48 个月、12 个月悬崖、4800 股：每月 100 股，悬崖节点 1200
        List<VestingSchedule.PlannedNode> nodes = VestingSchedule.plan(
                LocalDate.parse("2026-01-31"), new BigDecimal("4800"), 12, 48);

        assertThat(nodes.get(11).shares()).isEqualByComparingTo("1200");
        assertThat(nodes.get(12).shares()).isEqualByComparingTo("100");
        BigDecimal sum = nodes.stream().map(VestingSchedule.PlannedNode::shares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("4800");
    }

    @Test
    void oddSharesHalfUpRoundingSumsExactlyToTotal() {
        // 1000 股 / 6 个月：累计按 HALF_UP 取整，节点 = 相邻累计之差；
        // c(n)=round(1000n/6): 167,333,500,667,833,1000 → 节点 167,166,167,167,166,167
        List<VestingSchedule.PlannedNode> nodes = VestingSchedule.plan(
                LocalDate.parse("2026-01-15"), new BigDecimal("1000"), 0, 6);

        assertThat(nodes).extracting(VestingSchedule.PlannedNode::shares)
                .map(BigDecimal::intValueExact)
                .containsExactly(167, 166, 167, 167, 166, 167);
    }

    @Test
    void midMonthGrantKeepsSameDay() {
        List<VestingSchedule.PlannedNode> nodes = VestingSchedule.plan(
                LocalDate.parse("2026-01-15"), new BigDecimal("1200"), 0, 12);
        assertThat(nodes.get(0).nodeDate()).isEqualTo(LocalDate.parse("2026-02-15"));
        assertThat(nodes.get(11).nodeDate()).isEqualTo(LocalDate.parse("2027-01-15"));
    }

    @Test
    void rejectsCliffBeyondVesting() {
        assertThatThrownBy(() -> VestingSchedule.plan(
                LocalDate.parse("2026-01-31"), new BigDecimal("100"), 13, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
