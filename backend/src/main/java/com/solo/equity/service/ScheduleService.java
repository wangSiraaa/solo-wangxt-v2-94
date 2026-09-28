package com.solo.equity.service;

import com.solo.equity.domain.Grant;
import com.solo.equity.domain.VestingNode;
import com.solo.equity.repo.GrantRepository;
import com.solo.equity.repo.VestingNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds monthly vesting schedules.
 *
 * Quantity rule (all arithmetic in DECIMAL(18,4) share counts):
 * <ol>
 *   <li>{@code monthlySlice} = total / vestingMonths, rounded HALF_EVEN to 4 digits;</li>
 *   <li>the cliff node (month = cliffMonths) vests {@code monthlySlice * cliffMonths};</li>
 *   <li>every month after the cliff is its own node of {@code monthlySlice};</li>
 *   <li>the very last node absorbs the rounding remainder, so node quantities sum
 *       to the grant total <b>exactly</b>.</li>
 * </ol>
 *
 * Vest dates are "same day N months on": {@code LocalDate.plusMonths} naturally clamps
 * a month-end grant (Jan 31 + 1 month = Feb 28/29), so end-of-month awards keep their
 * end-of-month interpretation throughout the timeline.
 */
@Service
public class ScheduleService {

    static final int SHARE_SCALE = 4;

    private final GrantRepository grants;
    private final VestingNodeRepository nodes;

    public ScheduleService(GrantRepository grants, VestingNodeRepository nodes) {
        this.grants = grants;
        this.nodes = nodes;
    }

    @Transactional
    public Grant createGrant(String name, String grantee, LocalDate grantDate,
                             BigDecimal total, int cliffMonths, int vestingMonths,
                             String planName, List<Integer> conditionalMonths) {
        if (total == null || total.signum() <= 0) {
            throw new BusinessRuleException("Total quantity must be positive");
        }
        if (cliffMonths < 0 || cliffMonths >= vestingMonths) {
            throw new BusinessRuleException("cliffMonths must be >= 0 and < vestingMonths");
        }
        if (vestingMonths <= 0) {
            throw new BusinessRuleException("vestingMonths must be positive");
        }
        total = total.setScale(SHARE_SCALE, RoundingMode.HALF_EVEN);

        Grant grant = grants.save(new Grant(name, grantee, grantDate, total,
                cliffMonths, vestingMonths, planName));
        nodes.saveAll(buildNodes(grant, total, cliffMonths, vestingMonths,
                conditionalMonths == null ? List.of() : conditionalMonths));
        return grant;
    }

    private List<VestingNode> buildNodes(Grant grant, BigDecimal total, int cliffMonths,
                                         int vestingMonths, List<Integer> conditionalMonths) {
        // Even monthly slice; the final node of the schedule carries rounding remainder.
        BigDecimal monthly = total.divide(BigDecimal.valueOf(vestingMonths),
                SHARE_SCALE, RoundingMode.HALF_EVEN);

        List<int[]> nodeSpecs = new ArrayList<>(); // {seqNo, multiplier (number of slices)}
        if (cliffMonths > 0) {
            // The cliff node batches months 1..cliffMonths into a single node.
            nodeSpecs.add(new int[]{cliffMonths, cliffMonths});
        }
        // cliffMonths == 0 means monthly vesting starts at month 1 with no cliff batch.
        for (int m = cliffMonths + 1; m <= vestingMonths; m++) {
            nodeSpecs.add(new int[]{m, 1});
        }

        List<VestingNode> result = new ArrayList<>();
        java.util.Set<Integer> validSeqs = nodeSpecs.stream()
                .map(s -> s[0]).collect(java.util.stream.Collectors.toSet());
        for (Integer cm : conditionalMonths) {
            if (!validSeqs.contains(cm)) {
                throw new BusinessRuleException(
                        "Conditional month " + cm + " is not a vesting node (cliff months are one batch)");
            }
        }
        BigDecimal allocated = BigDecimal.ZERO.setScale(SHARE_SCALE, RoundingMode.HALF_EVEN);
        for (int i = 0; i < nodeSpecs.size(); i++) {
            int seq = nodeSpecs.get(i)[0];
            int slices = nodeSpecs.get(i)[1];
            boolean last = i == nodeSpecs.size() - 1;

            BigDecimal qty = last
                    ? total.subtract(allocated)
                    : monthly.multiply(BigDecimal.valueOf(slices))
                             .setScale(SHARE_SCALE, RoundingMode.HALF_EVEN);
            allocated = allocated.add(qty);

            LocalDate vestDate = grant.getGrantDate().plusMonths(seq);
            boolean conditional = conditionalMonths.contains(seq);
            result.add(new VestingNode(grant, seq, vestDate, qty,
                    cliffMonths > 0 && seq == cliffMonths, conditional));
        }
        return result;
    }

    @Transactional
    public void markConditionMet(Long grantId, int seqNo, LocalDate metDate) {
        VestingNode node = nodes.findByGrantIdOrderBySeqNoAsc(grantId).stream()
                .filter(n -> n.getSeqNo() == seqNo)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Vesting node " + seqNo + " of grant " + grantId + " not found"));
        if (!node.isConditional()) {
            throw new BusinessRuleException("Node " + seqNo + " is not conditional");
        }
        if (node.getConditionMetAt() != null) {
            throw new BusinessRuleException("Condition already marked satisfied");
        }
        node.markConditionMet(metDate);
    }
}
