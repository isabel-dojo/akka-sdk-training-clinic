package com.clinic.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record Appointment(String id, LocalDateTime dateTime, String doctorId, String patientId, String issue,
                          Optional<String> notes, List<String> prescriptions, Status status) {
    public enum Status {
        SCHEDULED,
        CANCELLED,
        COMPLETED,
        MISSED
    }

    public Appointment(String id, LocalDateTime dateTime, String doctorId, String patientId, String issue) {
        this(id, dateTime, doctorId, patientId, issue, Optional.empty(), List.of(), Status.SCHEDULED);
    }

    public Appointment reschedule(LocalDateTime newDateTime, String newDoctorId) {
        return new Appointment(id, newDateTime, newDoctorId, patientId, issue, notes, prescriptions, status);
    }

    public Appointment addNotes(String notes) {
        return new Appointment(id, dateTime, doctorId, patientId, issue, Optional.of(notes), prescriptions, status);
    }

    public Appointment addPrescription(String prescription) {
        var prescriptions = new ArrayList<>(this.prescriptions);
        prescriptions.add(prescription);
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, Collections.unmodifiableList(prescriptions), status);
    }

    public Appointment cancel() {
        if (status != Status.SCHEDULED)
            throw new IllegalStateException("Cannot cancel an appointment that is not scheduled");
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, Status.CANCELLED);
    }

    public Appointment complete() {
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, Status.COMPLETED);
    }

    public Appointment markAsMissed() {
        if (status != Status.SCHEDULED)
            throw new IllegalStateException("Cannot mark as missed an appointment that is not scheduled");
        return new Appointment(id, dateTime, doctorId, patientId, issue, notes, prescriptions, Status.MISSED);
    }
}
