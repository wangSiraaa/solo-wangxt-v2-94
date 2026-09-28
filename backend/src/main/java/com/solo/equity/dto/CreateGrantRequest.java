package com.solo.equity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateGrantRequest(
        @NotBlank String grantName,
        @NotBlank String grantee,
        @NotNull LocalDate grantDate,
        @NotNull @Positive BigDecimal totalQuantity,
        int cliffMonths,
        int vestingMonths,
        @NotBlank String planName,
        /** Month indices whose node only vests after a manual satisfaction mark. */
        List<Integer> conditionalMonths
) {
}
