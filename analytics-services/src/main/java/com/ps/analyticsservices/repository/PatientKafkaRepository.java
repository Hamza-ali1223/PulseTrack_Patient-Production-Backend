package com.ps.analyticsservices.repository;

import com.ps.analyticsservices.PatientEventDB;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PatientKafkaRepository extends JpaRepository<PatientEventDB, UUID> {
}
