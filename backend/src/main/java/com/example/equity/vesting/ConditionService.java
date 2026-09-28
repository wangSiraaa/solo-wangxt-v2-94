package com.example.equity.vesting;

import com.example.equity.domain.Grant;
import com.example.equity.domain.VestingNode;
import com.example.equity.domain.VestingNodeRepository;
import com.example.equity.grant.GrantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/** 人工标记条件批次满足；标记时刻决定该节点从哪天起计入归属。 */
@Service
public class ConditionService {

    private final GrantService grantService;
    private final VestingNodeRepository nodeRepository;
    private final Clock clock;

    public ConditionService(GrantService grantService,
                            VestingNodeRepository nodeRepository,
                            Clock clock) {
        this.grantService = grantService;
        this.nodeRepository = nodeRepository;
        this.clock = clock;
    }

    @Transactional
    public VestingNode markMet(Long grantId, int monthIndex) {
        Grant grant = grantService.requireById(grantId);
        VestingNode node = nodeRepository.findByGrantIdOrderByMonthIndexAsc(grant.getId()).stream()
                .filter(n -> n.getMonthIndex() == monthIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "归属节点不存在: 授予=" + grantId + " 月序=" + monthIndex));
        if (!node.isConditional()) {
            throw new IllegalArgumentException("该节点不是条件批次，无需标记: 月序=" + monthIndex);
        }
        node.markConditionMet(Instant.now(clock));
        return node;
    }

    /** 测试 / 数据校正用：按指定 UTC 时刻回填满足时间（生产登记路径不开放任意回填）。 */
    @Transactional
    public VestingNode markMetAt(Long grantId, int monthIndex, Instant at) {
        Grant grant = grantService.requireById(grantId);
        VestingNode node = nodeRepository.findByGrantIdOrderByMonthIndexAsc(grant.getId()).stream()
                .filter(n -> n.getMonthIndex() == monthIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "归属节点不存在: 授予=" + grantId + " 月序=" + monthIndex));
        if (!node.isConditional()) {
            throw new IllegalArgumentException("该节点不是条件批次，无需标记: 月序=" + monthIndex);
        }
        node.markConditionMet(at);
        return node;
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
