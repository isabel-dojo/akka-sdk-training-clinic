package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.Appointment;
import com.clinic.domain.RescheduleAppointmentState;
import com.clinic.domain.Schedule;
import dev.langchain4j.exception.HttpException;

import java.time.Duration;
import java.time.LocalDateTime;

@Component(id = "reschedule-appointment")
public class RescheduleAppointmentWorkflow extends Workflow<RescheduleAppointmentState> {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);
    private final ComponentClient componentClient;

    public RescheduleAppointmentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record RescheduleAppointmentCommand(LocalDateTime newDateTime,
                                               String newDoctorId
    ) {}

    public Effect<Done> startRescheduleAppointment(RescheduleAppointmentCommand cmd) {
        System.out.println("---- Starting Reschedule Appointment Workflow ----");

        if (currentState() != null) {
            return  effects().error("Appointment does not exist");
        }

        Appointment appointment = componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::getAppointment)
                .invoke()
                .orElseThrow(() -> new HttpException(404, "Appointment does not exist"));

        var state = new RescheduleAppointmentState(appointment.dateTime(),
                appointment.doctorId(),
                cmd.newDateTime(),
                cmd.newDoctorId(),
                appointment.patientId(),
                appointment.issue()
        );

        return effects()
                .updateState(state)
                .transitionTo(RescheduleAppointmentWorkflow::createNewTimeslot)
                .thenReply(Done.getInstance());
    }

    public StepEffect createNewTimeslot() {
        System.out.println("---- Creating New Timeslot ---");

        var scheduleId = new Schedule.ScheduleId(
                currentState().newDoctorId(),
                currentState().newDatetime().toLocalDate()
        );

        try {
            componentClient
                    .forKeyValueEntity(scheduleId.toString())
                    .method(ScheduleEntity::scheduleAppointment)
                    .invoke(new ScheduleEntity.ScheduleAppointmentData(
                            currentState().newDatetime().toLocalTime(),
                            DEFAULT_DURATION,
                            commandContext().workflowId()));
        } catch (Exception e) {
            System.out.println("Error creating New Timeslot");
            return stepEffects().thenTransitionTo(RescheduleAppointmentWorkflow::nothingHappens);
        }

        return stepEffects().thenTransitionTo(RescheduleAppointmentWorkflow::updateAppointment);
    }

    public StepEffect updateAppointment() {
        System.out.println("---- Updating Appointment ---");
        try {
            componentClient
                    .forEventSourcedEntity(commandContext().workflowId())
                    .method(AppointmentEntity::reschedule)
                    .invoke(new AppointmentEntity.RescheduleCmd(currentState().newDatetime(), currentState().newDoctorId()));
        } catch (Exception e) {
            System.out.println("Error updating Appointment");
            return stepEffects().thenTransitionTo(RescheduleAppointmentWorkflow::deleteNewTimeslot);
        }
        return stepEffects().thenTransitionTo(RescheduleAppointmentWorkflow::deleteOldTimeslot);
    }

    public StepEffect deleteNewTimeslot() {
        System.out.println("---- Deleting New Timeslot ---");
        var newScheduleId = new Schedule.ScheduleId(currentState().newDoctorId(), currentState().newDatetime().toLocalDate());

        componentClient
                .forKeyValueEntity(newScheduleId.toString())
                .method(ScheduleEntity::removeTimeSlot)
                .invoke(new ScheduleEntity.RemoveAppointmentData(
                      commandContext().workflowId(),
                      currentState().newDatetime().toLocalTime()
                ));

        return stepEffects().thenEnd();
    }

    public StepEffect deleteOldTimeslot() {
        System.out.println("---- Deleting Old Timeslot ---");

        var oldScheduleId = new Schedule.ScheduleId(currentState().oldDoctorId(), currentState().oldDateTime().toLocalDate());

        componentClient
                .forKeyValueEntity(oldScheduleId.toString())
                .method(ScheduleEntity::removeTimeSlot)
                .invoke(new ScheduleEntity.RemoveAppointmentData(
                        commandContext().workflowId(),
                        currentState().oldDateTime().toLocalTime()
                ));

        return stepEffects().thenEnd();
    }

    public StepEffect nothingHappens() {
        System.out.println("---- Nothing Happens ---");
        return stepEffects().thenEnd();
    }

}
