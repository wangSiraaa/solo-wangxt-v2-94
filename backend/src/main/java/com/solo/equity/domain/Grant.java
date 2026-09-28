package com.solo.equity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single equity award (fictional plan only).
 *
 * Quantity fields are stored as explicit share counts with 4 fractional digits:
 * whole-share plans still get exact month-by-month splits (e.g. 4800 / 48 = 100),
 * and plans whose total is not divisible by the node count remain exact in DECIMAL(18,4).
 */
@Entity
@Table(name = "grants")
public class Grant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grant_name", nullable = false, length = 120)
    private String grantName;

    @Column(name = "grantee", nullable = false, length = 120)
    private String grantee;

    @Column(name = "grant_date", nullable = false)
    private LocalDate grantDate;

    /** Total shares covered by the award. */
    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalQuantity;

    /** Number of months before the cliff node vests (e.g. 12). */
    @Column(name = "cliff_months", nullable = false)
    private int cliffMonths;

    /** Total number of monthly vesting nodes after the grant date (e.g. 48). */
    @Column(name = "vesting_months", nullable = false)
    private int vestingMonths;

    /** Free-text label of the fictional plan, e.g. "Founders Fictional Plan 2026". */
    @Column(name = "plan_name", nullable = false, length = 160)
    private String planName;

    protected Grant() {
        // for JPA
    }

    public Grant(String grantName, String grantee, LocalDate grantDate,
                 BigDecimal totalQuantity, int cliffMonths, int vestingMonths, String planName) {
        this.grantName = grantName;
        this.grantee = grantee;
        this.grantDate = grantDate;
        this.totalQuantity = totalQuantity;
        this.cliffMonths = cliffMonths;
        this.vestingMonths = vestingMonths;
        this.planName = planName;
    }

    public Long getId() { return id; }
    public String getGrantName() { return grantName; }
    public String getGrantee() { return grantee; }
    public LocalDate getGrantDate() { return grantDate; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public int getCliffMonths() { return cliffMonths; }
    public int getVestingMonths() { return vestingMonths; }
    public String getPlanName() { return planName; }
}
