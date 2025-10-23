package com.clinic.application;

import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Appointment;
import com.clinic.domain.Schedule;
import com.clinic.application.ScheduleEntity.RemoveAppointmentData;
import com.clinic.application.ScheduleEntity.ScheduleAppointmentData;
import com.clinic.application.ScheduleAppointmentWorkflow.ScheduleAppointmentCommand;
import com.clinic.application.RescheduleAppointmentWorkflow.RescheduleAppointmentCommand;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RescheduleAppointmentWorkflowIntegrationTest extends TestKitSupport {

    private final String DOCTOR_1 = "house";
    private final String DOCTOR_2 = "otherdoc";
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);


    private LocalTime time(String str) {
        return LocalTime.parse(str);
    }

    private LocalDateTime dateTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time);
    }

    private void createSchedule(String doctorId, LocalDate date) {
        // Assume working hours are 9:00 to 17:00 for valid schedule creation
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("09:00"), time("17:00")));
    }

    private void scheduleInitialAppointment(String appointmentId, String doctorId, LocalDate date, LocalTime time) {
        createSchedule(doctorId, date);
        componentClient
                .forWorkflow(appointmentId)
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentCommand(dateTime(date, time), doctorId, "p1", "initial issue"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Appointment> appointment = componentClient
                    .forEventSourcedEntity(appointmentId)
                    .method(AppointmentEntity::getAppointment)
                    .invoke();
            assertTrue(appointment.isPresent());
            assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());
        });
    }


    @Test
    public void testSuccessfulReschedule() {
        final String appointmentId = "resch_success_1";
        final LocalDate OLD_DATE = LocalDate.of(2032, 1, 10);
        final LocalDate NEW_DATE = LocalDate.of(2032, 1, 11);
        final LocalTime OLD_TIME = LocalTime.of(10, 0);
        final LocalTime NEW_TIME = LocalTime.of(14, 0);

        scheduleInitialAppointment(appointmentId, DOCTOR_1, OLD_DATE, OLD_TIME);
        createSchedule(DOCTOR_2, NEW_DATE);

        componentClient
                .forWorkflow(appointmentId)
                .method(RescheduleAppointmentWorkflow::startRescheduleAppointment)
                .invoke(new RescheduleAppointmentCommand(dateTime(NEW_DATE, NEW_TIME), DOCTOR_2));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Appointment> appointment = componentClient.forEventSourcedEntity(appointmentId)
                    .method(AppointmentEntity::getAppointment)
                    .invoke();
            assertTrue(appointment.isPresent());
            assertEquals(DOCTOR_2, appointment.get().doctorId());
            assertEquals(dateTime(NEW_DATE, NEW_TIME), appointment.get().dateTime());
        });

        var oldSchedule = componentClient
                .forKeyValueEntity(DOCTOR_1 + ":" + OLD_DATE)
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(0, oldSchedule.get().timeSlots().size(), "Old timeslot should be deleted");

        var newSchedule = componentClient.forKeyValueEntity(DOCTOR_2 + ":" + NEW_DATE)
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(1, newSchedule.get().timeSlots().size(), "New timeslot should be created");
        assertEquals(NEW_TIME, newSchedule.get().timeSlots().get(0).startTime());
    }



    @Test
    public void testRescheduleConflict_NewTimeslotFails() {
        final String appointmentId = "resch_fail_1";

        final LocalDate OLD_DATE = LocalDate.of(2032, 1, 20);
        final LocalDate NEW_DATE = LocalDate.of(2032, 1, 21);
        final LocalTime OLD_TIME = LocalTime.of(10, 0);
        final LocalTime CONFLICT_TIME = LocalTime.of(14, 0);

        scheduleInitialAppointment(appointmentId, DOCTOR_1, OLD_DATE, OLD_TIME);
        createSchedule(DOCTOR_1, NEW_DATE);

        componentClient
                .forKeyValueEntity(DOCTOR_1 + ":" + NEW_DATE)
                .method(ScheduleEntity::scheduleAppointment)
                .invoke(new ScheduleAppointmentData(CONFLICT_TIME, DEFAULT_DURATION, "blocking_appt"));

        componentClient
                .forWorkflow(appointmentId)
                .method(RescheduleAppointmentWorkflow::startRescheduleAppointment)
                .invoke(new RescheduleAppointmentCommand(dateTime(NEW_DATE, CONFLICT_TIME), DOCTOR_1));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Appointment> appt = componentClient.forEventSourcedEntity(appointmentId)
                    .method(AppointmentEntity::getAppointment)
                    .invoke();
            assertTrue(appt.isPresent());
            assertEquals(DOCTOR_1, appt.get().doctorId());
            assertEquals(dateTime(OLD_DATE, OLD_TIME), appt.get().dateTime(), "Appointment time should be the old time, proving failure");
            assertEquals(Appointment.Status.SCHEDULED, appt.get().status(), "Status should be SCHEDULED, proving no compensation or update occurred.");
        });

        var oldSchedule = componentClient.forKeyValueEntity(DOCTOR_1 + ":" + OLD_DATE)
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(1, oldSchedule.get().timeSlots().size(), "Old timeslot should NOT be deleted by 'nothingHappens'");

        var newSchedule = componentClient.forKeyValueEntity(DOCTOR_1 + ":" + NEW_DATE)
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(1, newSchedule.get().timeSlots().size(), "Conflict schedule should still have only the blocking slot.");
    }

}
