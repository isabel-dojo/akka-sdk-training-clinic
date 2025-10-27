package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Component(id = "schedule")
public class ScheduleEntity extends KeyValueEntity<Schedule> {
    private Schedule.ScheduleId entityId;

    public ScheduleEntity(KeyValueEntityContext context) {
        this.entityId = Schedule.ScheduleId.fromString(context.entityId());
    }

    public Effect<Done> createSchedule(Schedule.WorkingHours workingHours) {
        if (currentState() != null)
            return effects().error("Schedule already exists");

        var schedule = new Schedule(entityId, workingHours);
        return effects()
                .updateState(schedule)
                .thenReply(Done.getInstance());
    }

    public record ScheduleAppointmentData(LocalTime startTime, Duration duration, String appointmentId) {
    }

    public Effect<Done> scheduleAppointment(ScheduleAppointmentData data) {
        if (currentState() == null)
            return effects().error("Working hours aren't defined for the selected date");

        try {
            var newState = currentState()
                    .scheduleAppointment(data.startTime, data.duration, data.appointmentId);
            return effects().updateState(newState).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }

    public record RemoveAppointmentData(String appointmentId, LocalTime startTime) {}

    public Effect<Done> removeTimeSlot(RemoveAppointmentData data) {
        if (currentState() == null)
            return effects().error("Working hours aren't defined for the selected date");

        try {
            var newState = currentState().
                    removeTimeSlot(data.appointmentId,  data.startTime);
            return effects().updateState(newState).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error(e.getMessage());
        }
    }

    public Effect<List<String>> blockSchedule() {
        if (currentState() == null) {
            return effects().error("Schedule not found");
        }
        if (currentState().status() == Schedule.Status.BLOCKED) {
            // If already blocked, just return the list, don't error
            return effects().reply(currentState().timeSlots().stream()
                    .map(Schedule.TimeSchedule::appointmentId)
                    .toList());
        }

        var newSchedule = currentState().block();
        var appointmentIds = currentState().timeSlots().stream()
                .map(Schedule.TimeSchedule::appointmentId)
                .toList();

        return effects()
                .updateState(newSchedule)
                .thenReply(appointmentIds);
    }

    public Effect<Done> deleteSchedule() {
        if (currentState() == null) {
            return effects().error("Schedule not found");
        }

        var newSchedule = currentState().delete();
        return effects()
                .updateState(newSchedule)
                .thenReply(Done.getInstance());
    }

    public Effect<Optional<Schedule>> getSchedule() {
        return effects().reply(Optional.ofNullable(currentState()));
    }
}
