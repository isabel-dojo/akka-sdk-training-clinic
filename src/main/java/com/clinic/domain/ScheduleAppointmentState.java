package com.clinic.domain;

import java.time.LocalDateTime;

public record ScheduleAppointmentState(LocalDateTime dateTime, String doctorId, String patientId, String issue) {

}