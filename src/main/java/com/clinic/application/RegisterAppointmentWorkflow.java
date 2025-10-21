package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDateTime;

@Component(id = "schedule-appointment")
public class RegisterAppointmentWorkflow extends Workflow<RegisterAppointmentState> {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    private final ComponentClient componentClient;

    public RegisterAppointmentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record RegisterAppointmentCommand(LocalDateTime dateTime, String doctorId, String patientId, String issue) {}

    public Effect<Done> startRegistration(RegisterAppointmentCommand cmd) {
        System.out.println("## startRegistration");
        if (currentState() != null)
            return effects().error("Appointment already exists");

        var state = new RegisterAppointmentState(cmd.dateTime, cmd.doctorId, cmd.patientId, cmd.issue);
        return effects()
                .updateState(state)
                .transitionTo(RegisterAppointmentWorkflow::createAppointment)
                .thenReply(Done.getInstance());
    }

    public Effect<RegisterAppointmentState> getState() {
        return effects().reply(currentState());
    }

    public StepEffect createAppointment() {
        System.out.println("## createAppointment");
        componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::createAppointment)
                .invoke(new AppointmentEntity.CreateAppointmentCmd(currentState().dateTime(), currentState().doctorId(), currentState().patientId(), currentState().issue()));

        return stepEffects()
                .thenTransitionTo(RegisterAppointmentWorkflow::scheduleTimeSlot);
    }

    public StepEffect scheduleTimeSlot() {
        System.out.println("## scheduleTimeSlot");
        var scheduleId = new Schedule.ScheduleId(currentState().doctorId(), currentState().dateTime().toLocalDate());
        try {
            componentClient
                    .forKeyValueEntity(scheduleId.toString())
                    .method(ScheduleEntity::scheduleAppointment)
                    .invoke(new ScheduleEntity.ScheduleAppointmentData(currentState().dateTime().toLocalTime(), DEFAULT_DURATION, commandContext().workflowId()));
        } catch (Exception e) {
            return stepEffects().thenTransitionTo(RegisterAppointmentWorkflow::cancelAppointment);
        }

        return stepEffects()
                .thenTransitionTo(RegisterAppointmentWorkflow::markAppointmentAsScheduled);
    }

    public StepEffect markAppointmentAsScheduled() {
        System.out.println("## markAppointmentAsScheduled");
        componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::schedule)
                .invoke();

        return stepEffects().thenEnd();
    }

    public StepEffect cancelAppointment() {
        System.out.println("## cancelAppointment");
        componentClient
                .forEventSourcedEntity(commandContext().workflowId())
                .method(AppointmentEntity::cancel)
                .invoke();

        return stepEffects().thenEnd();
    }
}
