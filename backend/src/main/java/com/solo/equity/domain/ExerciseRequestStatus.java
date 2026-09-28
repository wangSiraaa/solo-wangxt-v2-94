package com.solo.equity.domain;

public enum ExerciseRequestStatus {
    /** Submitted, still consumes the exercisable pool. */
    PENDING,
    /** Confirmed: shares have been exercised and permanently leave the pool. */
    CONFIRMED,
    /** Cancelled before confirmation: reserved quantity is released back to the pool. */
    CANCELLED
}
