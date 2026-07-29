


package com.refiq.platform.calculation.internal.repository;

import com.refiq.platform.calculation.internal.repository.entity.CalculationResultEntity;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CalculationResultRepository extends JpaRepository<CalculationResultEntity, UUID> {


  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("""
    update CalculationResultEntity result
       set result.status = com.refiq.platform.calculation.internal.repository.entity.CalculationStatus.PROCESSING
     where result.id = :id
       and result.status = com.refiq.platform.calculation.internal.repository.entity.CalculationStatus.PENDING
    """)
  int claimPendingCalculation(@Param("id") UUID id);

}