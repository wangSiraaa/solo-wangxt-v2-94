package com.solo.equity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One vesting node of a grant schedule.
 *
 * A standard monthly plan has one node per vesting month; the cliff node carries
 * {@code cliffMonths} monthly slices at once (e.g. month 12 vests 12 x the monthly slice),
 * subsequent months vest one slice each.
 *
 * A conditional node does <b>not</b> vest merely because its vest date has passed:
 * an administrator must additionally mark its condition satisfied
 * ({@code conditionMetAt}). Its effective vest date is the later of the scheduled
 * vest date and that marking date.
 */
@Entity
@Table(name = "vesting_nodes")
public class VestingNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    /** Month index on the schedule, 1-based; the cliff node uses the cliff month. */
    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    /** Calendar date the node is scheduled to vest (end-of-month safe). */
    @Column(name = "vest_date", nullable = false)
    private LocalDate vestDate;

    /** Shares vesting at this node; nodes sum exactly to the grant total. */
    @Column(name = "scheduled_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal scheduledQuantity;

    /** True for the cliff batch (documentation/UI marker). */
    @Column(name = "cliff_node", nullable = false)
    private boolean cliffNode;

    /** When true the node waits for an explicit administrator satisfaction mark. */
    @Column(name = "conditional_flag", nullable = false)
    private boolean conditional;

    /** Administrator-set date on which the condition was satisfied; null until then. */
    @Column(name = "condition_met_at")
    private LocalDate conditionMetAt;

    protected VestingNode() {
    }

    public VestingNode(Grant grant, int seqNo, LocalDate vestDate,
                       BigDecimal scheduledQuantity, boolean cliffNode, boolean conditional) {
        this.grant = grant;
        this.seqNo = seqNo;
        this.vestDate = vestDate;
        this.scheduledQuantity = scheduledQuantity;
        this.cliffNode = cliffNode;
        this.conditional = conditional;
    }

    /**
     * Effective vest date:
     * <ul>
     *   <li>unconditional node &rarr; the scheduled date;</li>
     *   <li>conditional node not yet satisfied &rarr; null (never vests automatically);</li>
     *   <li>conditional node satisfied &rarr; later of scheduled date and satisfaction date.</li>
     * </ul>
     */
    public LocalDate effectiveVestDate() {
        if (!conditional) {
            return vestDate;
        }
        if (conditionMetAt == null) {
            return null;
        }
        return conditionMetAt.isAfter(vestDate) ? conditionMetAt : vestDate;
    }

    public Long getId() { return id; }
    public Grant getGrant() { return grant; }
    public int getSeqNo() { return seqNo; }
    public LocalDate getVestDate() { return vestDate; }
    public BigDecimal getScheduledQuantity() { return scheduledQuantity; }
    public boolean isCliffNode() { return cliffNode; }
    public boolean isConditional() { return conditional; }
    public LocalDate getConditionMetAt() { return conditionMetAt; }

    public void markConditionMet(LocalDate date) {
        this.conditionMetAt = date;
    }
}
