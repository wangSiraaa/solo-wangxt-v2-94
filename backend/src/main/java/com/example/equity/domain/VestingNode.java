package com.example.equity.domain;

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
import java.time.Instant;
import java.time.LocalDate;

/**
 * 归属节点：自授予日起第 monthIndex 个自然月的 nodeDate 归属 shares 股。
 * 悬崖期由“前 N 个月节点 shares=0、第 N 个月节点为累积量”表达。
 * 条件节点仅在人工标记 conditionMet 后才归属。
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

    @Column(name = "month_index", nullable = false)
    private int monthIndex;

    @Column(name = "node_date", nullable = false)
    private LocalDate nodeDate;

    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal shares;

    @Column(name = "is_conditional", nullable = false)
    private boolean conditional;

    @Column(name = "condition_label")
    private String conditionLabel;

    @Column(name = "condition_met", nullable = false)
    private boolean conditionMet;

    @Column(name = "satisfied_at")
    private Instant satisfiedAt;

    protected VestingNode() {
    }

    public VestingNode(Grant grant, int monthIndex, LocalDate nodeDate, BigDecimal shares) {
        this.grant = grant;
        this.monthIndex = monthIndex;
        this.nodeDate = nodeDate;
        this.shares = shares;
    }

    /** 该节点在查询日是否已经归属：到期 + 条件满足（条件按“满足时刻是否在查询日之前”回看）。 */
    public boolean isVestedAsOf(LocalDate asOf) {
        if (nodeDate.isAfter(asOf)) {
            return false;
        }
        if (!conditional) {
            return true;
        }
        if (!conditionMet || satisfiedAt == null) {
            return false;
        }
        // 条件满足以 UTC 日期回看；标记动作发生在当天即视为当天生效。
        LocalDate satisfiedDate = satisfiedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return !satisfiedDate.isAfter(asOf);
    }

    public Long getId() {
        return id;
    }

    public Grant getGrant() {
        return grant;
    }

    public int getMonthIndex() {
        return monthIndex;
    }

    public LocalDate getNodeDate() {
        return nodeDate;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public void setShares(BigDecimal shares) {
        this.shares = shares;
    }

    public boolean isConditional() {
        return conditional;
    }

    public void setConditional(boolean conditional) {
        this.conditional = conditional;
    }

    public String getConditionLabel() {
        return conditionLabel;
    }

    public void setConditionLabel(String conditionLabel) {
        this.conditionLabel = conditionLabel;
    }

    public boolean isConditionMet() {
        return conditionMet;
    }

    /** 由服务层在事务中设置，同时记录满足时刻。 */
    public void markConditionMet(Instant at) {
        if (this.conditionMet) {
            return;
        }
        this.conditionMet = true;
        this.satisfiedAt = at;
    }

    public Instant getSatisfiedAt() {
        return satisfiedAt;
    }
}
