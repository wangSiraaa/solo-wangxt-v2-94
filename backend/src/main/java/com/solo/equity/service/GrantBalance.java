package com.solo.equity.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Point-in-time quantities of one grant as seen on a query date.
 *
 * Every stage is reported separately on purpose &mdash; the UI must never collapse
 * the lifecycle into one remaining balance:
 *
 * <pre>
 * total = unvested + vested
 * vested = exercised + pendingReserved + exercisable
 * </pre>
 *
 * Where:
 * <ul>
 *   <li>{@code unvested} &mdash; scheduled nodes that have not vested on the query date
 *       (includes conditional batches whose condition is not yet marked met);</li>
 *   <li>{@code vested} &mdash; cumulative vested quantity as of the query date;</li>
 *   <li>{@code exercised} &mdash; confirmed exercise requests (confirmed on or before
 *       the query date);</li>
 *   <li>{@code pendingReserved} &mdash; PENDING requests reserving shares on that date
 *       (a request cancelled on date C is reserved only for dates before C);</li>
 *   <li>{@code exercisable} &mdash; still available for a new exercise request.</li>
 * </ul>
 */
public record GrantBalance(
        LocalDate queryDate,
        BigDecimal total,
        BigDecimal unvested,
        BigDecimal vested,
        BigDecimal exercised,
        BigDecimal pendingReserved,
        BigDecimal exercisable
) {
}
