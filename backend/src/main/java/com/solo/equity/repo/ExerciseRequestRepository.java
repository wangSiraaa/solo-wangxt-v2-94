package com.solo.equity.repo;

import com.solo.equity.domain.ExerciseRequest;
import com.solo.equity.domain.ExerciseRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRequestRepository extends JpaRepository<ExerciseRequest, Long> {

    List<ExerciseRequest> findByGrantIdOrderByRequestDateAscIdAsc(Long grantId);

    boolean existsByRequestNo(String requestNo);

    List<ExerciseRequest> findByGrantIdAndStatusOrderByIdAsc(Long grantId, ExerciseRequestStatus status);
}
