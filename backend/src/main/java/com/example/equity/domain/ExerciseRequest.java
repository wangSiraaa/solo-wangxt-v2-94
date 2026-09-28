package com.example.equity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.ZoneOffset;

/** 行权申请：PENDING（占用额度）→ CONFIRMED（成为已行权）或 CANCELLED（释放额度）。 */
@Entity
@Table(name = "exercise_requests")
public class ExerciseRequest {

    public enum Status {
        PENDING, CONFIRMED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected ExerciseRequest() {
    }

    public ExerciseRequest(Grant grant, BigDecimal quantity, Instant requestedAt) {
        this.grant = grant;
        this.quantity = quantity;
        this.status = Status.PENDING;
        this.requestedAt = requestedAt;
    }

    /** 按查询日回看：该申请在 asOf 当天是否处于给定状态（用 UTC 日期比较）。 */
    public boolean isStatusAsOf(Status expected, LocalDate asOf) {
        if (status != expected) {
            return false;
        }
        Instant effectiveAt = switch (expected) {
            case CONFIRMED -> confirmedAt;
            case CANCELLED -> cancelledAt;
            case PENDING -> requestedAt;
        };
        if (effectiveAt == null) {
            return false;
        }
        LocalDate effectiveDate = effectiveAt.atZone(ZoneOffset.UTC).toLocalDate();
        return !effectiveDate.isAfter(asOf);
    }

    public void confirm(Instant at) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("只有待确认申请可以确认，当前状态: " + status);
        }
        this.status = Status.CONFIRMED;
        this.confirmedAt = at;
    }

    public void cancel(Instant at) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("只有待确认申请可以取消，当前状态: " + status);
        }
        this.status = Status.CANCELLED;
        this.cancelledAt = at;
    }

    public Long getId() {
        return id;
    }

    public Grant getGrant() {
        return grant;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
