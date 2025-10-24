package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.clinic.domain.Doctor;

import java.util.List;
import java.util.Optional;

@Component(id = "doctor")
public class DoctorEntity extends KeyValueEntity<Doctor> {

    private final String entityId;

    public DoctorEntity(KeyValueEntityContext context) {
        this.entityId = context.entityId();
    }

    public record CreateDoctorCommand(String firstName,
                                      String lastName,
                                      List<String> specialities,
                                      String description,
                                      Optional<Doctor.Contact> contact) {}

    public Effect<Done> createDoctor(CreateDoctorCommand command) {
        if (currentState() != null) return effects().error("Doctor already exists");

        var newDoctor = new Doctor(entityId,
                command.firstName,
                command.lastName,
                command.specialities,
                command.description,
                command.contact);

        return effects()
                .updateState(newDoctor)
                .thenReply(Done.getInstance());
    }

    public Effect<Optional<Doctor>> getDoctor() {
        return effects().reply(Optional.ofNullable(currentState()));
    }
}
