package com.example.equity.grant;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GrantView(
        Long id,
        String name,
        String grantee,
        LocalDate grantDate,
        BigDecimal totalShares,
        int cliffMonths,
        int vestingMonths) {
}
