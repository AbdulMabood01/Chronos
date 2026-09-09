package com.maxwell.chronos.repository;

import com.maxwell.chronos.domain.VacationTypeEntity;
import com.maxwell.chronos.enums.VacationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VacationTypeRepository extends JpaRepository<VacationTypeEntity, Long> {
    Optional<VacationTypeEntity> findByName(VacationType name);
}
