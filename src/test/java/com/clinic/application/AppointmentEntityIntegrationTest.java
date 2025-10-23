package com.clinic.application;

import akka.javasdk.CommandException;
import akka.javasdk.testkit.TestKitSupport;
import com.clinic.domain.Appointment;
import jnr.constants.platform.Local;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

public class AppointmentEntityIntegrationTest extends TestKitSupport {

    private final String DOCTOR_ID = "house";
    private final String PATIENT_ID = "patient";
    private final String ISSUE = "issue";
    private final String DATE = "2032-01-01T10:00:00";

    @Test
    public void createAppointmentTest() {
        final String appointmentId = "a1";

        createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE);
        Optional<Appointment> appointment = getAppointment(appointmentId);

        assertTrue(appointment.isPresent());
        assertEquals(DOCTOR_ID, appointment.get().doctorId());
        assertEquals(LocalDateTime.parse("2032-01-01T10:00:00"), appointment.get().dateTime());
        assertTrue(appointment.get().notes().isEmpty());
        assertTrue(appointment.get().prescriptions().isEmpty());

        assertThrows(CommandException.class, () ->
                createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE));
    }

    @Test
    public void updateAppointmentTest() {
        final String appointmentId = "a2";

        createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE);

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::addNotes)
                .invoke("Notes!");

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::addPrescription)
                .invoke("Prescription!");

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::addPrescription)
                .invoke("Another prescription!");

        Optional<Appointment> appointment = getAppointment(appointmentId);
        assertTrue(appointment.isPresent());
        assertEquals("Notes!", appointment.get().notes().get());
        assertEquals(2,  appointment.get().prescriptions().size());
        assertTrue(appointment.get().prescriptions().contains("Prescription!"));
    }

    @Test
    public void rescheduleAppointmentTest() {
        final String appointmentId = "a3";
        final String newDate = "2032-01-02T10:00:00";

        createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE);

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::schedule)
                .invoke();

        Optional<Appointment> appointment = getAppointment(appointmentId);
        assertTrue(appointment.isPresent());
        assertEquals(Appointment.Status.SCHEDULED, appointment.get().status());

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::reschedule)
                .invoke(new AppointmentEntity.RescheduleCmd(dateTime(newDate), DOCTOR_ID));

        Optional<Appointment> rescheduledAppointment = getAppointment(appointmentId);
        assertTrue(rescheduledAppointment.isPresent());
        assertEquals(Appointment.Status.SCHEDULED, rescheduledAppointment.get().status());
        assertEquals(dateTime(newDate), rescheduledAppointment.get().dateTime());
        assertEquals(DOCTOR_ID, rescheduledAppointment.get().doctorId());
    }

    @Test
    public void completeAppointmentTest() {
        final String appointmentId = "a4";

        createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE);

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::schedule)
                .invoke();
        assertEquals(Appointment.Status.SCHEDULED, getAppointment(appointmentId).get().status());

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::complete)
                .invoke();
        assertEquals(Appointment.Status.COMPLETED, getAppointment(appointmentId).get().status());

        assertThrows(CommandException.class, () ->
            componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::cancel)
                .invoke()
        );
    }

    @Test
    public void cancelAppointmentFromPendingTest() {
        final String appointmentId = "a5";

        createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE);
        assertEquals(Appointment.Status.PENDING, getAppointment(appointmentId).get().status());

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::cancel)
                .invoke();
        assertEquals(Appointment.Status.CANCELLED, getAppointment(appointmentId).get().status());
    }

    @Test
    public void cancelAppointmentFromScheduledTest() {
        final String appointmentId = "a6";

        createAppointment(appointmentId, DATE, DOCTOR_ID, PATIENT_ID, ISSUE);
        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::schedule)
                .invoke();
        assertEquals(Appointment.Status.SCHEDULED, getAppointment(appointmentId).get().status());

        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::cancel)
                .invoke();
        assertEquals(Appointment.Status.CANCELLED, getAppointment(appointmentId).get().status());
    }


    private LocalDateTime dateTime(String time) {
        return LocalDateTime.parse(time);
    }

    private Optional<Appointment> getAppointment(String appointmentId) {
        return componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::getAppointment)
                .invoke();
    }

    private void createAppointment(String appointmentId, String date, String doctorId, String patientId, String issue) {
        componentClient
                .forEventSourcedEntity(appointmentId)
                .method(AppointmentEntity::createAppointment)
                .invoke(new AppointmentEntity.CreateAppointmentCmd(dateTime(date), doctorId, patientId, issue));
    }

}
