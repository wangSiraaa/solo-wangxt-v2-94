package com.solo.equity.web;

import com.solo.equity.domain.ExerciseRequest;
import com.solo.equity.dto.CreateExerciseRequest;
import com.solo.equity.dto.CreateGrantRequest;
import com.solo.equity.dto.GrantTimelineView;
import com.solo.equity.dto.MarkConditionRequest;
import com.solo.equity.repo.ExerciseRequestRepository;
import com.solo.equity.repo.GrantRepository;
import com.solo.equity.repo.VestingNodeRepository;
import com.solo.equity.service.BalanceService;
import com.solo.equity.service.ExerciseService;
import com.solo.equity.service.GrantBalance;
import com.solo.equity.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class EquityController {

    private final ScheduleService schedules;
    private final BalanceService balances;
    private final ExerciseService exercises;
    private final GrantRepository grants;
    private final VestingNodeRepository nodes;
    private final ExerciseRequestRepository requests;

    public EquityController(ScheduleService schedules, BalanceService balances,
                            ExerciseService exercises, GrantRepository grants,
                            VestingNodeRepository nodes, ExerciseRequestRepository requests) {
        this.schedules = schedules;
        this.balances = balances;
        this.exercises = exercises;
        this.grants = grants;
        this.nodes = nodes;
        this.requests = requests;
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantTimelineView createGrant(@Valid @RequestBody CreateGrantRequest req) {
        var grant = schedules.createGrant(req.grantName(), req.grantee(), req.grantDate(),
                req.totalQuantity(), req.cliffMonths(), req.vestingMonths(),
                req.planName(), req.conditionalMonths());
        // Fresh read in a new transaction (the create transaction has committed).
        return timeline(grant.getId(), grant.getGrantDate());
    }

    @GetMapping("/grants/{id}/timeline")
    public GrantTimelineView timeline(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var grant = grants.findById(id)
                .orElseThrow(() -> new com.solo.equity.service.NotFoundException("Grant " + id + " not found"));
        GrantBalance b = balances.balance(id, asOf);
        return GrantTimelineView.of(grant, b,
                nodes.findByGrantIdOrderBySeqNoAsc(id),
                requests.findByGrantIdOrderByRequestDateAscIdAsc(id));
    }

    @GetMapping("/grants")
    public Object listGrants() {
        return grants.findAll().stream()
                .map(g -> new Object() {
                    public final Long id = g.getId();
                    public final String grantName = g.getGrantName();
                    public final String grantee = g.getGrantee();
                    public final String planName = g.getPlanName();
                    public final LocalDate grantDate = g.getGrantDate();
                    public final java.math.BigDecimal totalQuantity = g.getTotalQuantity();
                    public final int cliffMonths = g.getCliffMonths();
                    public final int vestingMonths = g.getVestingMonths();
                })
                .toList();
    }

    @PostMapping("/grants/{id}/nodes/{seqNo}/mark-condition")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markCondition(@PathVariable Long id, @PathVariable int seqNo,
                              @Valid @RequestBody MarkConditionRequest req) {
        schedules.markConditionMet(id, seqNo, req.metDate());
    }

    @PostMapping("/grants/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantTimelineView requestExercise(@PathVariable Long id,
                                             @Valid @RequestBody CreateExerciseRequest req) {
        ExerciseRequest created = exercises.request(id, req.requestNo(),
                req.quantity(), req.requestDate());
        return timeline(id, created.getRequestDate());
    }

    @PostMapping("/exercises/{requestId}/confirm")
    public GrantTimelineView confirm(@PathVariable Long requestId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExerciseRequest r = exercises.confirm(requestId, date);
        return timeline(r.getGrant().getId(), date);
    }

    @PostMapping("/exercises/{requestId}/cancel")
    public GrantTimelineView cancel(@PathVariable Long requestId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExerciseRequest r = exercises.cancel(requestId, date);
        return timeline(r.getGrant().getId(), date);
    }
}
