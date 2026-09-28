package com.solo.equity.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MarkConditionRequest(@NotNull LocalDate metDate) {
}
