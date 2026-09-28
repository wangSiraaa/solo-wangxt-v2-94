package com.example.equity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VestingNodeRepository extends JpaRepository<VestingNode, Long> {

    List<VestingNode> findByGrantIdOrderByMonthIndexAsc(Long grantId);
}
