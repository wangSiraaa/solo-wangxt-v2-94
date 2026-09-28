package com.example.equity.grant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 登记授予：授予日、数量、悬崖月数、归属月数，以及可选的条件批次（按月序指定）。 */
public record RegisterGrantRequest(
        @NotBlank String name,
        @NotBlank String grantee,
        @NotNull LocalDate grantDate,
        @NotNull @Positive BigDecimal totalShares,
        @Min(0) int cliffMonths,
        @Positive int vestingMonths,
        @Valid List<ConditionalNodeSpec> conditionalNodes) {

    /** 将某个归属节点标记为“需要人工确认条件满足后才归属”。 */
    public record ConditionalNodeSpec(
            @NotNull @Positive Integer monthIndex,
            @NotBlank String conditionLabel) {
    }
}
