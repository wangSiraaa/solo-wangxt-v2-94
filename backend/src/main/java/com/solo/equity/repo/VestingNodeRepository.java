package com.solo.equity.repo;

import com.solo.equity.domain.VestingNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VestingNodeRepository extends JpaRepository<VestingNode, Long> {

    List<VestingNode> findByGrantIdOrderBySeqNoAsc(Long grantId);
}
