package com.solo.equity.domain;

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
import java.time.LocalDate;

/**
 * An exercise request against a grant.
 *
 * Lifecycle:
 * <pre>
 *   PENDING  --confirm-->   CONFIRMED   (exercised: totalVested includes it)
 *   PENDING  --cancel---->  CANCELLED   (reservation released: pool grows back)
 * </pre>
 * PENDING requests act as reservations so that two requests cannot over-subscribe
 * the same vested shares. The quantities never move into an "exercised" bucket
 * until confirmation.
 */
@Entity
@Table(name = "exercise_requests")
public class ExerciseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;

    @Column(name = "request_no", nullable = false, unique = true, length = 40)
    private String requestNo;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ExerciseRequestStatus status = ExerciseRequestStatus.PENDING;

    @Column(name = "confirmed_date")
    private LocalDate confirmedDate;

    @Column(name = "cancelled_date")
    private LocalDate cancelledDate;

    protected ExerciseRequest() {
    }

    public ExerciseRequest(Grant grant, String requestNo, BigDecimal quantity, LocalDate requestDate) {
        this.grant = grant;
        this.requestNo = requestNo;
        this.quantity = quantity;
        this.requestDate = requestDate;
        this.status = ExerciseRequestStatus.PENDING;
    }

    public void confirm(LocalDate date) {
        if (this.status != ExerciseRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be confirmed");
        }
        this.status = ExerciseRequestStatus.CONFIRMED;
        this.confirmedDate = date;
    }

    public void cancel(LocalDate date) {
        if (this.status != ExerciseRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be cancelled");
        }
        this.status = ExerciseRequestStatus.CANCELLED;
        this.cancelledDate = date;
    }

    public Long getId() { return id; }
    public Grant getGrant() { return grant; }
    public String getRequestNo() { return requestNo; }
    public BigDecimal getQuantity() { return quantity; }
    public LocalDate getRequestDate() { return requestDate; }
    public ExerciseRequestStatus getStatus() { return status; }
    public LocalDate getConfirmedDate() { return confirmedDate; }
    public LocalDate getCancelledDate() { return cancelledDate; }
}
