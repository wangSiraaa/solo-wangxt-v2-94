package com.example.equity.web;

import com.example.equity.domain.ExerciseRequest;
import com.example.equity.domain.Grant;
import com.example.equity.domain.VestingNode;
import com.example.equity.exercise.ExerciseRequestDto;
import com.example.equity.exercise.ExerciseService;
import com.example.equity.grant.GrantService;
import com.example.equity.grant.GrantView;
import com.example.equity.grant.RegisterGrantRequest;
import com.example.equity.vesting.ConditionService;
import com.example.equity.vesting.GrantSnapshot;
import com.example.equity.vesting.SnapshotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EquityController {

    private final GrantService grantService;
    private final SnapshotService snapshotService;
    private final ConditionService conditionService;
    private final ExerciseService exerciseService;

    public EquityController(GrantService grantService,
                            SnapshotService snapshotService,
                            ConditionService conditionService,
                            ExerciseService exerciseService) {
        this.grantService = grantService;
        this.snapshotService = snapshotService;
        this.conditionService = conditionService;
        this.exerciseService = exerciseService;
    }

    @PostMapping("/grants")
    public GrantView register(@Valid @RequestBody RegisterGrantRequest req) {
        return GrantService.toView(grantService.register(req));
    }

    @GetMapping("/grants")
    public List<GrantView> list() {
        return grantService.list().stream().map(GrantService::toView).toList();
    }

    /** 按查询日返回四阶段数量 + 节点 / 申请明细（额度解释）。 */
    @GetMapping("/grants/{id}/snapshot")
    public Map<String, Object> snapshot(@PathVariable Long id, @RequestParam LocalDate date) {
        Grant grant = grantService.requireById(id);
        SnapshotService.SnapshotDetail detail = snapshotService.snapshot(grant, date);
        GrantSnapshot s = detail.snapshot();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant", GrantService.toView(grant));
        body.put("asOfDate", date);
        body.put("quantities", new LinkedHashMap<String, Object>() {{
            put("total", s.total());
            put("unvested", s.unvested());
            put("vested", s.vested());
            put("exercised", s.exercised());
            put("pending", s.pending());
            put("exercisable", s.exercisable());
        }});
        body.put("nodes", detail.nodes().stream().map(EquityController::nodeJson).toList());
        body.put("requests", detail.requests().stream().map(EquityController::requestJson).toList());
        return body;
    }

    /** 人工标记条件批次满足（satisfiedAt 可指定日期用于时间轴核对，默认当前时刻）。 */
    @PostMapping("/grants/{id}/nodes/{monthIndex}/satisfy")
    public Map<String, Object> satisfy(@PathVariable Long id,
                                       @PathVariable int monthIndex,
                                       @RequestParam(required = false) String satisfiedAt) {
        Instant at = satisfiedAt == null
                ? Instant.now()
                : LocalDate.parse(satisfiedAt).atTime(12, 0).atOffset(java.time.ZoneOffset.UTC).toInstant();
        VestingNode node = conditionService.markMetAt(id, monthIndex, at);
        return Map.of("grantId", id, "monthIndex", monthIndex,
                "conditionMet", node.isConditionMet(),
                "satisfiedAt", node.getSatisfiedAt().toString());
    }

    @PostMapping("/grants/{id}/exercises")
    public Map<String, Object> exercise(@PathVariable Long id,
                                        @Valid @RequestBody ExerciseRequestDto dto) {
        return exerciseJson(exerciseService.request(id, dto.quantity(), dto.asOf()));
    }

    @PostMapping("/exercises/{requestId}/confirm")
    public Map<String, Object> confirm(@PathVariable Long requestId,
                                       @RequestParam(required = false) LocalDate date) {
        return exerciseJson(exerciseService.confirm(requestId, date));
    }

    @PostMapping("/exercises/{requestId}/cancel")
    public Map<String, Object> cancel(@PathVariable Long requestId,
                                      @RequestParam(required = false) LocalDate date) {
        return exerciseJson(exerciseService.cancel(requestId, date));
    }

    private static Map<String, Object> nodeJson(GrantSnapshot.NodeView n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("monthIndex", n.monthIndex());
        m.put("nodeDate", n.nodeDate());
        m.put("shares", n.shares());
        m.put("conditional", n.conditional());
        m.put("conditionLabel", n.conditionLabel() == null ? "" : n.conditionLabel());
        m.put("conditionMet", n.conditionMet());
        m.put("vestedDate", n.vestedDate());
        m.put("vestedAsOf", n.vestedAsOf());
        return m;
    }

    private static Map<String, Object> requestJson(GrantSnapshot.RequestView r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("quantity", r.quantity());
        m.put("status", r.status());
        m.put("requestedDate", r.requestedDate());
        m.put("confirmedDate", r.confirmedDate());
        m.put("cancelledDate", r.cancelledDate());
        m.put("occupiesAsOf", r.occupiesAsOf());
        return m;
    }

    /** 写操作响应用申请当前真实状态（非历史回看）。 */
    private static Map<String, Object> exerciseJson(ExerciseRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("grantId", r.getGrant().getId());
        m.put("quantity", r.getQuantity());
        m.put("status", r.getStatus().name());
        m.put("requestedAt", r.getRequestedAt());
        m.put("confirmedAt", r.getConfirmedAt());
        m.put("cancelledAt", r.getCancelledAt());
        return m;
    }
}
