


package com.refiq.platform.calculation.internal.repository;

import com.refiq.platform.calculation.internal.repository.entity.CalculationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CalculationResultRepository extends JpaRepository<CalculationResultEntity, UUID> {
  // Al usar el UUID del archivo como Primary Key, no necesitamos escribir
  // métodos custom como findByFileId().
  // Nos basta con el findById() que ya nos regala JpaRepository.
}