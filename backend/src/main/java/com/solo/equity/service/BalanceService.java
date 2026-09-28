package com.solo.equity.service;

import com.solo.equity.domain.ExerciseRequest;
import com.solo.equity.domain.ExerciseRequestStatus;
import com.solo.equity.domain.Grant;
import com.solo.equity.domain.VestingNode;
import com.solo.equity.repo.ExerciseRequestRepository;
import com.solo.equity.repo.GrantRepository;
import com.solo.equity.repo.VestingNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reconstructs every lifecycle stage of a grant on an arbitrary query date.
 *
 * It deliberately does <b>not</b> keep a single "remaining balance": vesting,
 * exercise confirmation and cancellation each live on their own timeline and must
 * be reconstructed independently for the queried day.
 */
@Service
public class BalanceService {

    private final GrantRepository grants;
    private final VestingNodeRepository nodes;
    private final ExerciseRequestRepository requests;

    public BalanceService(GrantRepository grants, VestingNodeRepository nodes,
                          ExerciseRequestRepository requests) {
        this.grants = grants;
        this.nodes = nodes;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public GrantBalance balance(Long grantId, LocalDate queryDate) {
        Grant grant = grants.findById(grantId)
                .orElseThrow(() -> new NotFoundException("Grant " + grantId + " not found"));

        BigDecimal vested = BigDecimal.ZERO;
        BigDecimal unvested = BigDecimal.ZERO;
        for (VestingNode node : nodes.findByGrantIdOrderBySeqNoAsc(grantId)) {
            LocalDate effective = node.effectiveVestDate();
            if (effective != null && !effective.isAfter(queryDate)) {
                vested = vested.add(node.getScheduledQuantity());
            } else {
                unvested = unvested.add(node.getScheduledQuantity());
            }
        }

        BigDecimal exercised = BigDecimal.ZERO;
        BigDecimal pendingReserved = BigDecimal.ZERO;
        for (ExerciseRequest req : requests.findByGrantIdOrderByRequestDateAscIdAsc(grantId)) {
            // A request filed in the future relative to the query date does not exist yet.
            if (req.getRequestDate().isAfter(queryDate)) {
                continue;
            }
            switch (effectiveStatus(req, queryDate)) {
                case CONFIRMED -> exercised = exercised.add(req.getQuantity());
                case PENDING -> pendingReserved = pendingReserved.add(req.getQuantity());
                case CANCELLED -> { /* released: contributes to neither bucket */ }
            }
        }

        BigDecimal exercisable = vested.subtract(exercised).subtract(pendingReserved);
        return new GrantBalance(queryDate, grant.getTotalQuantity(),
                unvested.stripTrailingZeros(), vested.stripTrailingZeros(),
                exercised.stripTrailingZeros(), pendingReserved.stripTrailingZeros(),
                exercisable.stripTrailingZeros());
    }

    /**
     * Status of a request <em>as seen on the query date</em> (its request date is
     * already known to be on or before the query date). A request confirmed or
     * cancelled on day C still looks PENDING on days before C.
     */
    private ExerciseRequestStatus effectiveStatus(ExerciseRequest req, LocalDate queryDate) {
        if (req.getStatus() == ExerciseRequestStatus.CONFIRMED
                && req.getConfirmedDate() != null
                && !req.getConfirmedDate().isAfter(queryDate)) {
            return ExerciseRequestStatus.CONFIRMED;
        }
        if (req.getStatus() == ExerciseRequestStatus.CANCELLED
                && req.getCancelledDate() != null
                && !req.getCancelledDate().isAfter(queryDate)) {
            return ExerciseRequestStatus.CANCELLED;
        }
        return ExerciseRequestStatus.PENDING;
    }
}
