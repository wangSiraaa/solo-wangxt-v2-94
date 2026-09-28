package com.example.equity.exercise;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发起行权申请。
 * asOf 可指定“按哪一天的额度校验/记账”（演示时间轴核对）；为空则用当天。
 */
public record ExerciseRequestDto(
        @NotNull @Positive BigDecimal quantity,
        LocalDate asOf) {
}
