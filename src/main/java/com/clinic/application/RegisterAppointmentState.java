package com.clinic.application;

import java.time.LocalDateTime;

public record RegisterAppointmentState(LocalDateTime dateTime, String doctorId, String patientId, String issue) {

}