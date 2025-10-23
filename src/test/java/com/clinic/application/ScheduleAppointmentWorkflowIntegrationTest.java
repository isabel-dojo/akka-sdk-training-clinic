package com.clinic.application;

import akka.javasdk.CommandException;
import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Appointment;
import com.clinic.domain.Schedule;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleAppointmentWorkflowIntegrationTest extends TestKitSupport {

    private final String TEST_DOCTOR_ID = "house";

    @Test
    public void scheduleAppointment() {
        // Setup working hours for the Schedule Entity
        componentClient
                .forKeyValueEntity(TEST_DOCTOR_ID + ":2031-10-20")
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));

        // Start the workflow
        componentClient
                .forWorkflow("1")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-20T11:00:00"), TEST_DOCTOR_ID, "2", "issue"));

        // Verify the Appointment Entity is scheduled
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appointment = componentClient
                            .forEventSourcedEntity("1")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appointment.isPresent());
                    assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());
                });

        // Verify the Schedule Entity has one time slot
        var updatedSchedule = componentClient.forKeyValueEntity(TEST_DOCTOR_ID + ":2031-10-20")
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(1, updatedSchedule.get().timeSlots().size());
    }

    @Test
    public void scheduleTwice() {
        // Setup and schedule appointment
        componentClient
                .forKeyValueEntity(TEST_DOCTOR_ID + ":2031-10-21")
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));

        componentClient
                .forWorkflow("2")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-21T11:00:00"), TEST_DOCTOR_ID, "p3", "issue1"));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appointment = componentClient
                            .forEventSourcedEntity("2")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appointment.isPresent());
                    assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());
                });

        // Attempting to schedule a second appointment on the same ID throws exception
        assertThrows(CommandException.class, () ->
                componentClient
                        .forWorkflow("2")
                        .method(ScheduleAppointmentWorkflow::schedule)
                        .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-21T12:00:00"), TEST_DOCTOR_ID, "p3", "issue2"))
        );

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appointment = componentClient
                            .forEventSourcedEntity("2")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    // Status remains SCHEDULED from the first run
                    assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());
                    // Time remains the original time (11:00:00), proving the second call was blocked
                    assertEquals(dateTime("2031-10-21T11:00:00"), appointment.get().dateTime());
                });
    }

    @Test
    public void scheduleOverlapping() {
        String dateKey = TEST_DOCTOR_ID + ":2031-10-22";

        // Create Schedule
        componentClient
                .forKeyValueEntity(dateKey)
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time("10:00"), time("16:00")));

        // Schedule appointment
        componentClient
                .forWorkflow("3")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-22T11:00:00"), TEST_DOCTOR_ID, "p4", "issue"));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
            Optional<Appointment> appointment = componentClient
                    .forEventSourcedEntity("3")
                    .method(AppointmentEntity::getAppointment)
                    .invoke();
            assertTrue(appointment.isPresent());
            assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());
        });

        // Schedule second overlapping appointment
        componentClient
                .forWorkflow("4")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-22T11:15:00"), TEST_DOCTOR_ID, "p5", "overlapping issue"));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appointment = componentClient
                            .forEventSourcedEntity("4")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appointment.isPresent());
                    assertEquals(Appointment.Status.CANCELLED, appointment.get().status());
                });

        var updatedSchedule = componentClient
                .forKeyValueEntity(dateKey)
                .method(ScheduleEntity::getSchedule)
                .invoke();
        assertEquals(1, updatedSchedule.get().timeSlots().size());
    }

    @Test
    public void scheduleDoesntExist() {
        componentClient
                .forWorkflow("5")
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(dateTime("2031-10-23T12:00:00"), TEST_DOCTOR_ID, "p6", "no schedule issue"));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<Appointment> appointment = componentClient
                            .forEventSourcedEntity("5")
                            .method(AppointmentEntity::getAppointment)
                            .invoke();
                    assertTrue(appointment.isPresent());
                    assertEquals(Appointment.Status.CANCELLED, appointment.get().status());
                });
    }

    private LocalDate date(String str) {
        return LocalDate.parse(str);
    }

    private LocalTime time(String str) {
        return LocalTime.parse(str);
    }

    private LocalDateTime dateTime(String str) {
        return LocalDateTime.parse(str);
    }
}