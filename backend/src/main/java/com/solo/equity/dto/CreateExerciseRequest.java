package com.solo.equity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExerciseRequest(
        @NotBlank String requestNo,
        @NotNull @Positive BigDecimal quantity,
        @NotNull LocalDate requestDate
) {
}
