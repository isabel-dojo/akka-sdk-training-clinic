package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Component(id = "delete-schedule")
public class DeleteScheduleWorkflow extends Workflow<DeleteScheduleWorkflow.DeleteScheduleState> {

    private final ComponentClient componentClient;

    public DeleteScheduleWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record DeleteScheduleState(String doctorId, LocalDate date, List<String> appointmentsToCancel) {}
    public record DeleteScheduleCommand(String doctorId, LocalDate date) {}

    public Effect<Done> start(DeleteScheduleCommand cmd) {
        if (currentState() != null) {
            return effects().error("Workflow already running for this schedule deletion.");
        }
        var state = new DeleteScheduleState(cmd.doctorId, cmd.date, List.of());
        return effects()
                .updateState(state)
                .transitionTo(DeleteScheduleWorkflow::blockSchedule)
                .thenReply(Done.getInstance());
    }

    public StepEffect blockSchedule() {
        var scheduleId = new Schedule.ScheduleId(currentState().doctorId(), currentState().date()).toString();

        List<String> appointmentIds;
        try {
            appointmentIds = componentClient
                    .forKeyValueEntity(scheduleId)
                    .method(ScheduleEntity::blockSchedule)
                    .invoke();
        } catch (Exception e) {
            System.err.println("Failed to block schedule " + scheduleId + ": " + e.getMessage());
            return stepEffects().thenEnd();
        }

        var newState = new DeleteScheduleState(
                currentState().doctorId(),
                currentState().date(),
                appointmentIds
        );

        return stepEffects()
                .updateState(newState)
                .thenTransitionTo(DeleteScheduleWorkflow::processNextAppointment);
    }


    public StepEffect processNextAppointment() {
        var appointmentsToCancel = currentState().appointmentsToCancel();

        if (appointmentsToCancel.isEmpty()) {
            return stepEffects().thenEnd();
        }

        var appointmentId = appointmentsToCancel.get(0);
        var remainingAppointments = appointmentsToCancel.stream().skip(1).toList();

        var newState = new DeleteScheduleState(
                currentState().doctorId(),
                currentState().date(),
                remainingAppointments
        );

        try {
            componentClient
                    .forEventSourcedEntity(appointmentId)
                    .method(AppointmentEntity::cancel)
                    .invoke();
        } catch (Exception e) {
            System.err.println("Failed to cancel appointment " + appointmentId + ": " + e.getMessage());
        }

        return stepEffects()
                .updateState(newState)
                .thenTransitionTo(DeleteScheduleWorkflow::processNextAppointment);
    }


    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                .stepTimeout(DeleteScheduleWorkflow::processNextAppointment, Duration.ofSeconds(10))
                .build();
    }
}