package com.example.equity.grant;

import com.example.equity.domain.Grant;
import com.example.equity.domain.GrantRepository;
import com.example.equity.domain.VestingNode;
import com.example.equity.domain.VestingNodeRepository;
import com.example.equity.vesting.VestingSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 登记授予并一次性展开悬崖期 / 月度归属节点。 */
@Service
public class GrantService {

    private final GrantRepository grantRepository;
    private final VestingNodeRepository nodeRepository;

    public GrantService(GrantRepository grantRepository, VestingNodeRepository nodeRepository) {
        this.grantRepository = grantRepository;
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    public Grant register(RegisterGrantRequest req) {
        if (grantRepository.findByName(req.name()).isPresent()) {
            throw new IllegalArgumentException("授予名称已存在: " + req.name());
        }
        Grant grant = grantRepository.save(new Grant(req.name(), req.grantee(), req.grantDate(),
                req.totalShares(), req.cliffMonths(), req.vestingMonths()));

        List<VestingSchedule.PlannedNode> planned = VestingSchedule.plan(
                req.grantDate(), req.totalShares(), req.cliffMonths(), req.vestingMonths());

        Map<Integer, String> conditionalByMonth = req.conditionalNodes() == null
                ? Map.of()
                : req.conditionalNodes().stream().collect(Collectors.toMap(
                        RegisterGrantRequest.ConditionalNodeSpec::monthIndex,
                        RegisterGrantRequest.ConditionalNodeSpec::conditionLabel));

        Set<Integer> seen = new HashSet<>();
        List<VestingNode> nodes = planned.stream().map(p -> {
            VestingNode node = new VestingNode(grant, p.monthIndex(), p.nodeDate(), p.shares());
            String label = conditionalByMonth.get(p.monthIndex());
            if (label != null) {
                if (!seen.add(p.monthIndex())) {
                    throw new IllegalArgumentException("条件批次的月序重复: " + p.monthIndex());
                }
                node.setConditional(true);
                node.setConditionLabel(label);
            }
            return node;
        }).toList();
        nodeRepository.saveAll(nodes);
        return grant;
    }

    @Transactional(readOnly = true)
    public List<Grant> list() {
        return grantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Grant requireById(Long id) {
        return grantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("授予不存在: id=" + id));
    }

    public static GrantView toView(Grant g) {
        return new GrantView(g.getId(), g.getName(), g.getGrantee(), g.getGrantDate(),
                g.getTotalShares(), g.getCliffMonths(), g.getVestingMonths());
    }
}
