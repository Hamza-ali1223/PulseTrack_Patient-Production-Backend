package com.ps.patientservice.controller;

import com.ps.patientservice.dto.PatientCreateDTO;
import com.ps.patientservice.dto.PatientDTO;
import com.ps.patientservice.dto.PatientUpdateGroup;
import com.ps.patientservice.grpc.BillingServiceGrpcClient;
import com.ps.patientservice.mapper.PatientMapper;
import com.ps.patientservice.model.Patient;
import com.ps.patientservice.service.PatientService;

import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
@Tag(
        name = "Patient API",
        description = "Endpoints for creating, retrieving, updating, and deleting patient records"
)
public class PatientController {

    private final PatientService patientService;


    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(
            summary = "Get all patients",
            description = "Returns a list of all patient records in the system."
    )
    public ResponseEntity<List<PatientDTO>> getAllPatients() {
        return ResponseEntity.ok(
                patientService.getAll().stream().map(PatientMapper::toDTO).toList()
        );
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get patient by ID",
            description = "Retrieve a single patient's details using their unique UUID."
    )
    public ResponseEntity<PatientDTO> getPatientById(@PathVariable String id) {
        UUID uuid = UUID.fromString(id);
        return ResponseEntity.ok(PatientMapper.toDTO(patientService.getById(uuid)));
    }

    @PostMapping
    @Operation(
            summary = "Create a new patient",
            description = """
                    Creates a new patient record.  
                    All fields in PatientCreateDTO are required, including `registeredDate`.
                    """
    )
    public ResponseEntity<Patient> savePatient(
            @Validated({Default.class, PatientUpdateGroup.class})
            @RequestBody PatientCreateDTO patient
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patientService.savePatient(PatientMapper.fromDTO(patient)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a patient",
            description = "Delete an existing patient using their UUID. Returns a confirmation message on success."
    )
    public ResponseEntity<String> deleteById(@PathVariable String id) {
        UUID uuid = UUID.fromString(id);
        if (patientService.deleteById(uuid)) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body("Success: Deleted Patient of ID: " + uuid.toString());
        }
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an existing patient",
            description = """
                    Updates an existing patient record.  
                    All fields except `registeredDate` must follow default validation rules.  
                    
                    **Important:**  
                    - `registeredDate` is **optional** during update (it belongs to the Default group only).  
                    - If `registeredDate` is null, the update still succeeds.  
                    """
    )
    public ResponseEntity<PatientDTO> updatePatient(
            @PathVariable String id,
            @Validated(Default.class) @RequestBody PatientCreateDTO patientDTO
    ) {
        PatientDTO patientResponse =
                PatientMapper.toDTO(patientService.updatePatient(UUID.fromString(id), patientDTO));

        if (patientResponse.getId() == null) {
            return ResponseEntity.badRequest().body(patientResponse);
        }
        return ResponseEntity.ok(patientResponse);
    }
}
