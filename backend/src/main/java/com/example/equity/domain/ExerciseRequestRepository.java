package com.example.equity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRequestRepository extends JpaRepository<ExerciseRequest, Long> {

    List<ExerciseRequest> findByGrantIdOrderByRequestedAtAsc(Long grantId);
}
