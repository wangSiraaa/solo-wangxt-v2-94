package com.solo.equity.service;

import com.solo.equity.domain.ExerciseRequest;
import com.solo.equity.domain.Grant;
import com.solo.equity.repo.ExerciseRequestRepository;
import com.solo.equity.repo.GrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creates, confirms and cancels exercise requests.
 *
 * A new PENDING request is only accepted up to the exercisable pool on its request
 * date (vested minus already exercised minus other PENDING reservations active that
 * day). Cancelling an unconfirmed request releases its reservation immediately.
 */
@Service
public class ExerciseService {

    private final ExerciseRequestRepository requests;
    private final GrantRepository grants;
    private final BalanceService balances;

    public ExerciseService(ExerciseRequestRepository requests, GrantRepository grants,
                           BalanceService balances) {
        this.requests = requests;
        this.grants = grants;
        this.balances = balances;
    }

    @Transactional
    public ExerciseRequest request(Long grantId, String requestNo, BigDecimal quantity,
                                   LocalDate requestDate) {
        Grant grant = grants.findById(grantId)
                .orElseThrow(() -> new NotFoundException("Grant " + grantId + " not found"));
        if (requests.existsByRequestNo(requestNo)) {
            throw new BusinessRuleException("Request number already exists: " + requestNo);
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("Exercise quantity must be positive");
        }
        quantity = quantity.setScale(ScheduleService.SHARE_SCALE);

        GrantBalance b = balances.balance(grantId, requestDate);
        if (quantity.compareTo(b.exercisable()) > 0) {
            throw new BusinessRuleException(String.format(
                    "Exercise request %s exceeds exercisable quantity on %s: requested %s, exercisable %s",
                    requestNo, requestDate, quantity.toPlainString(), b.exercisable().toPlainString()));
        }
        return requests.save(new ExerciseRequest(grant, requestNo, quantity, requestDate));
    }

    @Transactional
    public ExerciseRequest confirm(Long requestId, LocalDate confirmedDate) {
        ExerciseRequest req = mustFind(requestId);
        req.confirm(confirmedDate);
        return req;
    }

    @Transactional
    public ExerciseRequest cancel(Long requestId, LocalDate cancelledDate) {
        ExerciseRequest req = mustFind(requestId);
        req.cancel(cancelledDate);
        return req;
    }

    private ExerciseRequest mustFind(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new NotFoundException("Exercise request " + id + " not found"));
    }
}
