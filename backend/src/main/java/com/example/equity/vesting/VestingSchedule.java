package com.example.equity.vesting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 虚构归属计划的节点切分规则：
 * <ul>
 *   <li>授予日之后每经过一个自然月产生一个节点，节点日为该月对应日；
 *       月底授予（如 1/31）自动落在各月最后一天（2/28、4/30 …）。</li>
 *   <li>前 cliffMonths-1 个节点数量为 0；第 cliffMonths 个节点一次性归属悬崖累积量。</li>
 *   <li>每个节点的“累计应归属” = round(总数 × 月序 / 总月数, HALF_UP)，
 *       节点数量 = 相邻累计之差；舍入误差被最后一个节点自动吸收，节点合计严格等于总数。</li>
 * </ul>
 * 本类为纯函数、无副作用，全部计算使用 BigDecimal（0 位小数，明确整数股精度）。
 */
public final class VestingSchedule {

    public record PlannedNode(int monthIndex, LocalDate nodeDate, BigDecimal shares) {
    }

    private VestingSchedule() {
    }

    /**
     * 生成 monthIndex = 1..vestingMonths 的全部节点。
     */
    public static java.util.List<PlannedNode> plan(LocalDate grantDate,
                                                   BigDecimal totalShares,
                                                   int cliffMonths,
                                                   int vestingMonths) {
        if (vestingMonths <= 0) {
            throw new IllegalArgumentException("归属月数必须大于 0");
        }
        if (cliffMonths < 0 || cliffMonths > vestingMonths) {
            throw new IllegalArgumentException("悬崖月数必须在 0 与归属总月数之间");
        }
        if (totalShares.signum() <= 0) {
            throw new IllegalArgumentException("授予数量必须大于 0");
        }

        YearMonth grantMonth = YearMonth.from(grantDate);
        boolean grantAtMonthEnd = grantDate.equals(grantMonth.atEndOfMonth());

        java.util.List<PlannedNode> nodes = new java.util.ArrayList<>(vestingMonths);
        for (int n = 1; n <= vestingMonths; n++) {
            YearMonth targetMonth = grantMonth.plusMonths(n);
            LocalDate nodeDate = grantAtMonthEnd
                    ? targetMonth.atEndOfMonth()
                    : clampDay(grantDate.getDayOfMonth(), targetMonth);

            BigDecimal shares = nodeShares(totalShares, n, cliffMonths, vestingMonths);
            nodes.add(new PlannedNode(n, nodeDate, shares));
        }

        // 防御性自检：节点合计必须严格等于授予总数（不允许靠一个“余额数字”掩盖误差）。
        BigDecimal sum = nodes.stream().map(PlannedNode::shares).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(totalShares) != 0) {
            throw new IllegalStateException("归属节点合计 " + sum + " 与授予总数 " + totalShares + " 不一致");
        }
        return nodes;
    }

    private static BigDecimal nodeShares(BigDecimal total, int monthIndex, int cliffMonths, int vestingMonths) {
        return cumulative(total, monthIndex, cliffMonths, vestingMonths)
                .subtract(cumulative(total, monthIndex - 1, cliffMonths, vestingMonths));
    }

    /** 截至第 n 个节点应累计归属的整数股数；悬崖到期之前（n &lt; cliffMonths）累计为 0。 */
    private static BigDecimal cumulative(BigDecimal total, int n, int cliffMonths, int vestingMonths) {
        if (n <= 0 || n < cliffMonths) {
            return BigDecimal.ZERO;
        }
        if (n >= vestingMonths) {
            return total;
        }
        return total.multiply(BigDecimal.valueOf(n))
                .divide(BigDecimal.valueOf(vestingMonths), 0, RoundingMode.HALF_UP);
    }

    private static LocalDate clampDay(int dayOfMonth, YearMonth month) {
        return LocalDate.of(month.getYear(), month.getMonth(),
                Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
