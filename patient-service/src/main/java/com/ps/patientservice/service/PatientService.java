package com.ps.patientservice.service;

import com.ps.patientservice.dto.PatientCreateDTO;
import com.ps.patientservice.dto.PatientDTO;
import com.ps.patientservice.exception.EmailAreadyExistsException;
import com.ps.patientservice.exception.PatientNotFoundException;
import com.ps.patientservice.grpc.BillingServiceGrpcClient;
import com.ps.patientservice.kakfa.KafkaProducer;
import com.ps.patientservice.model.Patient;
import com.ps.patientservice.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService
{

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaProducer kafkaProducer;

    public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceGrpcClient, KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    public List<Patient> getAll()
    {
        return  patientRepository.findAll();
    }

    public Patient getById(UUID id)
    {
        return patientRepository.findById(id).orElse(null);
    }

    public Patient savePatient(Patient patient)
    {
        if(patientRepository.existsPatientByEmail(patient.getEmail()))
        {
            throw new EmailAreadyExistsException("A Patient with this Email: "+patient.getEmail()+" exists.");
        }

        Patient saved = patientRepository.save(patient);
        //Below our GRPC protobuf request to our BillingService
        billingServiceGrpcClient.createBillingAccount(saved.getId().toString(),saved.getName(),saved.getEmail());
        //Calling Kafka Producer to send Patient Event
        kafkaProducer.sendEvent(saved);
        return saved;
    }

    public Boolean deleteById(UUID id)
    {
        if(patientRepository.existsById(id))
        {
            patientRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public Patient updatePatient(UUID id, PatientCreateDTO dto) {

        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with Id: "+id.toString()));

        // Update allowed fields
        existing.setName(dto.getName());
        existing.setEmail(dto.getEmail());
        existing.setAddress(dto.getAddress());
        existing.setDateofBirth(dto.getDateofBirth());
       if(dto.getRegisteredDate()!=null)
           existing.setRegisteredDate(dto.getRegisteredDate());

        return patientRepository.save(existing);
    }







}
