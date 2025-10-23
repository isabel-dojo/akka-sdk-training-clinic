package com.clinic.application;

import akka.javasdk.testkit.TestKitSupport;
import akka.remote.artery.aeron.TaskRunner;
import com.clinic.domain.Appointment;
import com.clinic.domain.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static com.clinic.application.DateUtils.time;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ScheduleEntityIntegrationTest extends TestKitSupport {

    @Test
    public void checkOverlapping() {
        String doctorId = "house";
        String date = "2031-10-20";

        createSchedule(doctorId, date ,"10:00","16:00");
        scheduleAppointment(doctorId, date ,"11:00", "a1" );
        assertEquals(1, getSchedule(doctorId, date).get().timeSlots().size());

        assertThrows(IllegalArgumentException.class, () ->
                scheduleAppointment(doctorId, date ,"11:00", "a2" )
        );

        assertEquals(1, getSchedule(doctorId, date).get().timeSlots().size());
    }

    @Test
    public void checkOutOfWorkingHours() {
        String doctorId = "house";
        String date = "2031-10-21";

        createSchedule(doctorId, date ,"10:00","16:00");

        assertThrows(IllegalArgumentException.class, () ->
                scheduleAppointment(doctorId, date ,"09:00", "a1" )
        );

        assertThrows(IllegalArgumentException.class, () ->
                scheduleAppointment(doctorId, date ,"17:00", "a1" )
        );

        assertEquals(0, getSchedule(doctorId, date).get().timeSlots().size());
    }

    @Test
    public void checkDuplicateSchedule() {
        String doctorId = "house";
        String date = "2031-10-22";

        createSchedule(doctorId, date ,"10:00","16:00");

        assertThrows(IllegalArgumentException.class, () ->
                createSchedule(doctorId, date ,"11:00", "17:00" )
        );

        assertEquals(time("10:00"), getSchedule("house", "2031-10-22").get().workingHours().startTime());
    }

    @Test
    public void checkRemoveAppointment() {
        final String date = "2031-10-24";
        final String doctorId = "house";
        final String appointmentTime = "11:00";
        final String appointmentId = "remove-appointment";

        createSchedule(doctorId, date ,"10:00","16:00");
        scheduleAppointment(doctorId, date ,appointmentTime, appointmentId );
        assertEquals(1, getSchedule(doctorId, date).get().timeSlots().size());

        // Remove appointment
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::removeTimeSlot)
                .invoke(new ScheduleEntity.RemoveAppointmentData(appointmentId, time(appointmentTime)));

        assertEquals(0, getSchedule(doctorId, date).get().timeSlots().size());
    }

    private void createSchedule(String doctorId, String date, String startTime, String endTime) {
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::createSchedule)
                .invoke(new Schedule.WorkingHours(time(startTime), time(endTime)));
    }

    private Optional<Schedule> getSchedule(String doctorId, String date) {
        return componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::getSchedule)
                .invoke();
    }

    private void scheduleAppointment(String doctorId, String date, String startTime, String appointmentId) {
        componentClient
                .forKeyValueEntity(doctorId + ":" + date)
                .method(ScheduleEntity::scheduleAppointment)
                .invoke(new ScheduleEntity.ScheduleAppointmentData(time(startTime), Duration.ofMinutes(30), appointmentId));
    }
}
