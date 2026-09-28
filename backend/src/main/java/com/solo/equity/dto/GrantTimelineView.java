package com.solo.equity.dto;

import com.solo.equity.domain.ExerciseRequest;
import com.solo.equity.domain.Grant;
import com.solo.equity.domain.VestingNode;
import com.solo.equity.service.GrantBalance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the Angular timeline view needs for one grant on one query date. */
public record GrantTimelineView(
        Long grantId,
        String grantName,
        String grantee,
        String planName,
        LocalDate grantDate,
        BigDecimal totalQuantity,
        int cliffMonths,
        int vestingMonths,
        BalanceView balance,
        List<NodeView> nodes,
        List<RequestView> requests
) {

    public record BalanceView(
            LocalDate queryDate,
            BigDecimal total,
            BigDecimal unvested,
            BigDecimal vested,
            BigDecimal exercised,
            BigDecimal pendingReserved,
            BigDecimal exercisable
    ) {
        static BalanceView of(GrantBalance b) {
            return new BalanceView(b.queryDate(), b.total(), b.unvested(), b.vested(),
                    b.exercised(), b.pendingReserved(), b.exercisable());
        }
    }

    public record NodeView(
            int seqNo,
            LocalDate vestDate,
            BigDecimal scheduledQuantity,
            boolean cliffNode,
            boolean conditional,
            LocalDate conditionMetAt,
            LocalDate effectiveVestDate,
            boolean vestedAsOf
    ) {
        static NodeView of(VestingNode n, LocalDate queryDate) {
            LocalDate eff = n.effectiveVestDate();
            return new NodeView(n.getSeqNo(), n.getVestDate(), n.getScheduledQuantity(),
                    n.isCliffNode(), n.isConditional(), n.getConditionMetAt(), eff,
                    eff != null && !eff.isAfter(queryDate));
        }
    }

    public record RequestView(
            Long id,
            String requestNo,
            BigDecimal quantity,
            LocalDate requestDate,
            String status,
            LocalDate confirmedDate,
            LocalDate cancelledDate
    ) {
        static RequestView of(ExerciseRequest r) {
            return new RequestView(r.getId(), r.getRequestNo(), r.getQuantity(),
                    r.getRequestDate(), r.getStatus().name(),
                    r.getConfirmedDate(), r.getCancelledDate());
        }
    }

    public static GrantTimelineView of(Grant g, GrantBalance b,
                                       List<VestingNode> nodes, List<ExerciseRequest> reqs) {
        return new GrantTimelineView(
                g.getId(), g.getGrantName(), g.getGrantee(), g.getPlanName(),
                g.getGrantDate(), g.getTotalQuantity(), g.getCliffMonths(), g.getVestingMonths(),
                BalanceView.of(b),
                nodes.stream().map(n -> NodeView.of(n, b.queryDate())).toList(),
                reqs.stream().map(RequestView::of).toList());
    }
}
