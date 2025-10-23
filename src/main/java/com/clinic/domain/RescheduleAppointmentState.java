package com.clinic.domain;

import java.time.LocalDateTime;

public record RescheduleAppointmentState(LocalDateTime oldDateTime,
                                         String oldDoctorId,
                                         LocalDateTime newDatetime,
                                         String newDoctorId,
                                         String patientId,
                                         String issue
                                         ) {
}
