package com.solo.equity.repo;

import com.solo.equity.domain.Grant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrantRepository extends JpaRepository<Grant, Long> {
}
