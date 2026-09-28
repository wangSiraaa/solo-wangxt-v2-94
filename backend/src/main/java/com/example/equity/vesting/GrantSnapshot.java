package com.example.equity.vesting;

import com.example.equity.domain.ExerciseRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 某笔授予在某个查询日的四阶段数量快照。
 * 四个阶段各自独立展示，不允许只用一个“余额”掩盖差异：
 * <pre>
 * totalShares = unvested + vested
 * vested      = exercised + exercisable + pending
 * exercisable = vested − exercised − pending（被待确认申请占用之外的部分）
 * </pre>
 *
 * @param date        查询日
 * @param total       授予总数
 * @param unvested    尚未归属（含未到期、条件未满足）
 * @param vested      截至查询日累计已归属
 * @param exercised   截至查询日已确认行权
 * @param pending     截至查询日待确认、占用额度的申请数量
 * @param exercisable 尚可发起行权的数量
 */
public record GrantSnapshot(
        LocalDate date,
        BigDecimal total,
        BigDecimal unvested,
        BigDecimal vested,
        BigDecimal exercised,
        BigDecimal pending,
        BigDecimal exercisable) {

    /** 不可变为负；若为负说明数据不自洽（例如超出可行权额度的脏数据）。 */
    public boolean isConsistent() {
        return unvested.signum() >= 0
                && vested.signum() >= 0
                && exercised.signum() >= 0
                && pending.signum() >= 0
                && exercisable.signum() >= 0
                && unvested.add(vested).compareTo(total) == 0
                && exercised.add(exercisable).add(pending).compareTo(vested) == 0;
    }

    /** 节点视角明细。 */
    public record NodeView(
            int monthIndex,
            LocalDate nodeDate,
            BigDecimal shares,
            boolean conditional,
            String conditionLabel,
            boolean conditionMet,
            LocalDate vestedDate,   // 实际计入归属的日期：普通节点为 nodeDate，条件节点为标记满足日（未满足为 null）
            boolean vestedAsOf) {

        public static NodeView of(com.example.equity.domain.VestingNode n, LocalDate asOf) {
            LocalDate vestedDate = null;
            if (!n.isConditional()) {
                vestedDate = n.getNodeDate();
            } else if (n.isConditionMet() && n.getSatisfiedAt() != null) {
                vestedDate = n.getSatisfiedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            }
            return new NodeView(n.getMonthIndex(), n.getNodeDate(), n.getShares(),
                    n.isConditional(), n.getConditionLabel(), n.isConditionMet(),
                    vestedDate, n.isVestedAsOf(asOf));
        }
    }

    /** 行权申请视角明细，按查询日回看当时状态。 */
    public record RequestView(
            Long id,
            BigDecimal quantity,
            String status,
            LocalDate requestedDate,
            LocalDate confirmedDate,
            LocalDate cancelledDate,
            boolean occupiesAsOf) {

        /**
         * 按查询日回看申请在当天的状态：
         * <pre>
         * D &lt; 申请日            → NONE（尚未发生，不参与任何数量）
         * 申请日 ≤ D &lt; 终态日    → PENDING（占用可行权额度）
         * D ≥ 确认日            → CONFIRMED（计入已行权）
         * D ≥ 取消日            → CANCELLED（已释放，不参与任何数量）
         * </pre>
         */
        public static String statusAsOf(ExerciseRequest r, LocalDate asOf) {
            java.time.ZoneOffset utc = java.time.ZoneOffset.UTC;
            LocalDate requested = r.getRequestedAt().atZone(utc).toLocalDate();
            if (requested.isAfter(asOf)) {
                return "NONE";
            }
            return switch (r.getStatus()) {
                case PENDING -> "PENDING";
                case CONFIRMED -> {
                    LocalDate confirmed = r.getConfirmedAt().atZone(utc).toLocalDate();
                    yield confirmed.isAfter(asOf) ? "PENDING" : "CONFIRMED";
                }
                case CANCELLED -> {
                    LocalDate cancelled = r.getCancelledAt().atZone(utc).toLocalDate();
                    yield cancelled.isAfter(asOf) ? "PENDING" : "CANCELLED";
                }
            };
        }

        public static RequestView of(ExerciseRequest r, LocalDate asOf) {
            java.time.ZoneOffset utc = java.time.ZoneOffset.UTC;
            LocalDate requested = r.getRequestedAt().atZone(utc).toLocalDate();
            LocalDate confirmed = r.getConfirmedAt() == null ? null : r.getConfirmedAt().atZone(utc).toLocalDate();
            LocalDate cancelled = r.getCancelledAt() == null ? null : r.getCancelledAt().atZone(utc).toLocalDate();
            return new RequestView(r.getId(), r.getQuantity(), r.getStatus().name(),
                    requested, confirmed, cancelled,
                    statusAsOf(r, asOf).equals("PENDING"));
        }
    }
}
