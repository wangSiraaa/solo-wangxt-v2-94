package com.example.equity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 一笔授予：授予日、总数、悬崖月数、归属总月数。 */
@Entity
@Table(name = "grants")
public class Grant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String grantee;

    @Column(name = "grant_date", nullable = false)
    private LocalDate grantDate;

    @Column(name = "total_shares", nullable = false, precision = 18, scale = 0)
    private BigDecimal totalShares;

    @Column(name = "cliff_months", nullable = false)
    private int cliffMonths;

    @Column(name = "vesting_months", nullable = false)
    private int vestingMonths;

    protected Grant() {
    }

    public Grant(String name, String grantee, LocalDate grantDate,
                 BigDecimal totalShares, int cliffMonths, int vestingMonths) {
        this.name = name;
        this.grantee = grantee;
        this.grantDate = grantDate;
        this.totalShares = totalShares;
        this.cliffMonths = cliffMonths;
        this.vestingMonths = vestingMonths;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGrantee() {
        return grantee;
    }

    public void setGrantee(String grantee) {
        this.grantee = grantee;
    }

    public LocalDate getGrantDate() {
        return grantDate;
    }

    public void setGrantDate(LocalDate grantDate) {
        this.grantDate = grantDate;
    }

    public BigDecimal getTotalShares() {
        return totalShares;
    }

    public void setTotalShares(BigDecimal totalShares) {
        this.totalShares = totalShares;
    }

    public int getCliffMonths() {
        return cliffMonths;
    }

    public void setCliffMonths(int cliffMonths) {
        this.cliffMonths = cliffMonths;
    }

    public int getVestingMonths() {
        return vestingMonths;
    }

    public void setVestingMonths(int vestingMonths) {
        this.vestingMonths = vestingMonths;
    }
}
