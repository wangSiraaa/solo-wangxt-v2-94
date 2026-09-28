package com.example.equity.exercise;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 行权申请数量超过当日可行权额度（422 语义：业务规则不允许）。 */
public class ExceedsExercisableException extends RuntimeException {

    private final BigDecimal requested;
    private final BigDecimal available;
    private final LocalDate date;

    public ExceedsExercisableException(BigDecimal requested, BigDecimal available, LocalDate date) {
        super("行权申请 " + requested + " 股超过 " + date + " 可行权额度 " + available + " 股");
        this.requested = requested;
        this.available = available;
        this.date = date;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public LocalDate getDate() {
        return date;
    }
}
