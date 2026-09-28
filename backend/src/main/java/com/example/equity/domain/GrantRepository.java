package com.example.equity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrantRepository extends JpaRepository<Grant, Long> {
    Optional<Grant> findByName(String name);
}
