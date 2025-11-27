package com.ps.analyticsservices;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ps.analyticsservices.repository.PatientKafkaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

import java.util.UUID;

@Slf4j
@Service
public class KakfaConsumerService
{

    private final PatientKafkaRepository patientKafkaRepository;

    public KakfaConsumerService(PatientKafkaRepository patientKafkaRepository) {
        this.patientKafkaRepository = patientKafkaRepository;
    }

    @KafkaListener(topics = "patient", groupId = "analytics-service")
    public void ConsumeEvent(byte[] event)
    {
        try {
            PatientEvent patientEvent = PatientEvent.parseFrom(event);

            log.info("Received Patient Event: {}", patientEvent.toString());

            // ... perform business logic

            PatientEventDB dbEvent = new PatientEventDB();
            dbEvent.setEventType(patientEvent.getEventType());
            dbEvent.setEventPatientId(patientEvent.getPatientId());
            dbEvent.setPatientMail(patientEvent.getEmail());
            dbEvent.setPatientName(patientEvent.getName());

            PatientEventDB save = patientKafkaRepository.save(dbEvent);

            if(save != null)
                {
                log.info("Saved Patient Event: {}", save.toString());
                }

        } catch (InvalidProtocolBufferException e) {
            log.error("Error Parsing PatientEvent Event from KafkaConsumer: {} with error : {}",event, e);
        }
    }

}
