package com.example.equity.exercise;

import com.example.equity.domain.ExerciseRequest;
import com.example.equity.domain.ExerciseRequestRepository;
import com.example.equity.domain.Grant;
import com.example.equity.grant.GrantService;
import com.example.equity.vesting.ConditionService;
import com.example.equity.vesting.SnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 行权申请生命周期：
 * 发起（校验不得超过当日可行权额度，申请立即占用额度）→ 确认（成为已行权）/ 取消（释放额度）。
 */
@Service
public class ExerciseService {

    private final GrantService grantService;
    private final ExerciseRequestRepository requestRepository;
    private final SnapshotService snapshotService;
    private final Clock clock;

    public ExerciseService(GrantService grantService,
                           ExerciseRequestRepository requestRepository,
                           SnapshotService snapshotService,
                           Clock clock) {
        this.grantService = grantService;
        this.requestRepository = requestRepository;
        this.snapshotService = snapshotService;
        this.clock = clock;
    }

    @Transactional
    public ExerciseRequest request(Long grantId, BigDecimal quantity, LocalDate asOf) {
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("行权数量必须大于 0");
        }
        Grant grant = grantService.requireById(grantId);
        LocalDate effectiveDate = asOf != null ? asOf : LocalDate.now(clock);

        // 以申请日为查询日重算四阶段数量，确保不超过当时的可行权额度
        BigDecimal available = snapshotService.snapshot(grant, effectiveDate).snapshot().exercisable();
        if (quantity.compareTo(available) > 0) {
            throw new ExceedsExercisableException(quantity, available, effectiveDate);
        }

        // 以申请日中午（UTC）记账；真实系统由操作时刻决定，演示中用固定时刻保证按日回看确定
        Instant requestedAt = effectiveDate.atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant();
        ExerciseRequest request = new ExerciseRequest(grant, quantity, requestedAt);
        return requestRepository.save(request);
    }

    @Transactional
    public ExerciseRequest confirm(Long requestId, LocalDate asOf) {
        ExerciseRequest request = require(requestId);
        Instant at = asOf != null
                ? asOf.atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant()
                : Instant.now(clock);
        request.confirm(at);
        return request;
    }

    /** 取消未确认申请：PENDING → CANCELLED，对应额度在取消日起释放。 */
    @Transactional
    public ExerciseRequest cancel(Long requestId, LocalDate asOf) {
        ExerciseRequest request = require(requestId);
        Instant at = asOf != null
                ? asOf.atTime(12, 0).atOffset(ZoneOffset.UTC).toInstant()
                : Instant.now(clock);
        request.cancel(at);
        return request;
    }

    @Transactional(readOnly = true)
    public ExerciseRequest require(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("行权申请不存在: id=" + id));
    }

    public static LocalDate dateOf(Instant instant) {
        return ConditionService.dateOf(instant);
    }
}
