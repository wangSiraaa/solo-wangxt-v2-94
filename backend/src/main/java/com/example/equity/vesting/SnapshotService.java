package com.example.equity.vesting;

import com.example.equity.domain.ExerciseRequest;
import com.example.equity.domain.ExerciseRequestRepository;
import com.example.equity.domain.Grant;
import com.example.equity.domain.VestingNode;
import com.example.equity.domain.VestingNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 按查询日计算一笔授予的四阶段数量。
 * 数量全部从节点 / 申请明细逐条汇总（BigDecimal），不是用单一余额推算。
 */
@Service
public class SnapshotService {

    private final VestingNodeRepository nodeRepository;
    private final ExerciseRequestRepository requestRepository;

    public SnapshotService(VestingNodeRepository nodeRepository,
                           ExerciseRequestRepository requestRepository) {
        this.nodeRepository = nodeRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional(readOnly = true)
    public SnapshotDetail snapshot(Grant grant, LocalDate asOf) {
        List<VestingNode> nodes = nodeRepository.findByGrantIdOrderByMonthIndexAsc(grant.getId());
        List<ExerciseRequest> requests = requestRepository.findByGrantIdOrderByRequestedAtAsc(grant.getId());

        BigDecimal vested = BigDecimal.ZERO;
        List<GrantSnapshot.NodeView> nodeViews = new java.util.ArrayList<>(nodes.size());
        for (VestingNode node : nodes) {
            if (node.isVestedAsOf(asOf)) {
                vested = vested.add(node.getShares());
            }
            nodeViews.add(GrantSnapshot.NodeView.of(node, asOf));
        }

        BigDecimal exercised = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        List<GrantSnapshot.RequestView> requestViews = new java.util.ArrayList<>(requests.size());
        for (ExerciseRequest request : requests) {
            switch (GrantSnapshot.RequestView.statusAsOf(request, asOf)) {
                case "CONFIRMED" -> exercised = exercised.add(request.getQuantity());
                case "PENDING" -> pending = pending.add(request.getQuantity());
                default -> { /* NONE / CANCELLED：尚未发生或已释放，不计入 */ }
            }
            requestViews.add(GrantSnapshot.RequestView.of(request, asOf));
        }

        BigDecimal total = grant.getTotalShares();
        BigDecimal unvested = total.subtract(vested);
        BigDecimal exercisable = vested.subtract(exercised).subtract(pending);

        GrantSnapshot snapshot = new GrantSnapshot(asOf, total, unvested, vested, exercised, pending, exercisable);
        if (!snapshot.isConsistent()) {
            throw new IllegalStateException("授予 " + grant.getId() + " 在 " + asOf + " 的数量不平：" + snapshot);
        }
        return new SnapshotDetail(grant, snapshot, nodeViews, requestViews);
    }

    /** 当前可行权额度（行权申请校验时使用）。 */
    @Transactional(readOnly = true)
    public BigDecimal exercisable(Grant grant, LocalDate asOf) {
        return snapshot(grant, asOf).snapshot().exercisable();
    }

    public record SnapshotDetail(
            Grant grant,
            GrantSnapshot snapshot,
            List<GrantSnapshot.NodeView> nodes,
            List<GrantSnapshot.RequestView> requests) {
    }
}
