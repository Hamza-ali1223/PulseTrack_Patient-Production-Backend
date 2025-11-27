package com.ps.analyticsservices;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.UUID;

@Entity
@Data
public class PatientEventDB
{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    private String eventPatientId;
    private String patientName;
    private String patientMail;
    private String eventType;
}
