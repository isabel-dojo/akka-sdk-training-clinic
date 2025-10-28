package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.clinic.application.ai.DoctorFinderAgent;
import com.clinic.application.ai.UrgencyAgent;
import com.clinic.domain.Appointment;
import com.clinic.domain.Doctor;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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
            return stepEffects().thenTransitionTo(DeleteScheduleWorkflow::softDeleteSchedule);
        }

        return stepEffects().thenTransitionTo(DeleteScheduleWorkflow::attemptReschedule);
    }

    public StepEffect attemptReschedule() {
        var appointmentsToCancel = currentState().appointmentsToCancel();
        var appointmentId = appointmentsToCancel.get(0);
        var remainingAppointments = appointmentsToCancel.stream().skip(1).toList();

        System.out.println("Attempting to reschedule appointment " + appointmentId);

        var newState = new DeleteScheduleState(
                currentState().doctorId(),
                currentState().date(),
                remainingAppointments
        );

        try {
            Appointment appointment = componentClient
                    .forEventSourcedEntity(appointmentId)
                    .method(AppointmentEntity::getAppointment)
                    .invoke()
                    .orElse(null);

            if (appointment == null) {
                System.out.println("Appointment " + appointmentId + " not found");
                return stepEffects()
                        .updateState(newState)
                        .thenTransitionTo(DeleteScheduleWorkflow::processNextAppointment);
            }

            var urgencySession = commandContext().workflowId() + "-" + appointmentId;
            String urgency = componentClient
                    .forAgent()
                    .inSession(urgencySession)
                    .method(UrgencyAgent::urgency)
                    .invoke(appointment.issue())
                    .trim()
                    .toLowerCase();

            Optional<RescheduleAppointmentWorkflow.RescheduleAppointmentCommand> newSlot =
                    findNextAvailableSlot(appointment, urgency);

            if (newSlot.isPresent()) {
                System.out.println("There is a new slot available!!!!");
                componentClient
                        .forWorkflow(appointmentId)
                        .method(RescheduleAppointmentWorkflow::startRescheduleAppointment)
                        .invoke(newSlot.get());
            } else {
                System.out.println("There is no slot available!!!!");
                componentClient
                        .forEventSourcedEntity(appointmentId)
                        .method(AppointmentEntity::cancel)
                        .invoke();
            }

        } catch (Exception e) {
            System.err.println("Failed to process appointment " + appointmentId + ": " + e.getMessage());
        }

        return stepEffects()
                .updateState(newState)
                .thenTransitionTo(DeleteScheduleWorkflow::processNextAppointment);
    }

    public StepEffect softDeleteSchedule() {
        var scheduleId = new Schedule.ScheduleId(currentState().doctorId(), currentState().date()).toString();
        try {
            componentClient
                    .forKeyValueEntity(scheduleId)
                    .method(ScheduleEntity::deleteSchedule)
                    .invoke();
        } catch (Exception e) {
            System.err.println("Failed to soft-delete schedule " + scheduleId + ": " + e.getMessage());
        }
        return stepEffects().thenEnd();
    }


    private Optional<RescheduleAppointmentWorkflow.RescheduleAppointmentCommand> findNextAvailableSlot(Appointment appointment, String urgency) {
        try {
            System.out.println("..........Trying to find a new slot for issue: " + appointment.issue() + "..........");

            // Use the DoctorFinderAgent
            var agentSession = commandContext().workflowId() + "-" + appointment.id();
            String speciality = componentClient
                    .forAgent()
                    .inSession(agentSession)
                    .method(DoctorFinderAgent::getSpecialityForIssue)
                    .invoke(appointment.issue())
                    .trim(); // Trim whitespace

            System.out.println("AI determined speciality: " + speciality);

            // Safety check / fallback
            if (speciality.isEmpty() || speciality.length() > 50) {
                System.out.println("AI returned invalid speciality, falling back to original doctor's speciality.");
                Doctor originalDoctor = componentClient
                        .forKeyValueEntity(appointment.doctorId())
                        .method(DoctorEntity::getDoctor)
                        .invoke()
                        .orElseThrow(() -> new RuntimeException("Original doctor not found"));
                if (originalDoctor.specialities().isEmpty()) {
                    return Optional.empty(); // No speciality to search on
                }
                speciality = originalDoctor.specialities().getFirst();
            }

            List<DoctorsView.DoctorRow> similarDoctors = componentClient
                    .forView()
                    .method(DoctorsView::findBySpeciality)
                    .invoke(new DoctorsView.FindBySpecialityQuery(speciality))
                    .doctors();

            System.out.println("List of similar doctors in " + speciality + ": " + similarDoctors);

            LocalDate searchStartDate;
            int searchDurationDays;
            LocalDate originalDate = appointment.dateTime().toLocalDate();
            LocalDate today = LocalDate.now();

            switch (urgency) {
                case "high":
                    searchStartDate = today;
                    searchDurationDays = 7;
                    System.out.println("HIGH urgency: Searching for 7 days starting from today.");
                    break;
                case "medium":
                    searchStartDate = originalDate.isBefore(today) ? today : originalDate;
                    searchDurationDays = 7;
                    System.out.println("MEDIUM urgency: Searching for 7 days starting from " + searchStartDate);
                    break;
                case "low":
                default:
                    LocalDate potentialStartDate = originalDate.plusDays(1);
                    searchStartDate = potentialStartDate.isBefore(today) ? today : potentialStartDate;
                    searchDurationDays = 14;
                    System.out.println("LOW urgency: Searching for 14 days starting from " + searchStartDate);
                    break;
            }

            LocalDate searchDate = searchStartDate;
            for (int i = 0; i < searchDurationDays; i++) {
                for (DoctorsView.DoctorRow doctor : similarDoctors) {
                    var scheduleId = new Schedule.ScheduleId(doctor.id(), searchDate).toString();
                    Schedule schedule = componentClient
                            .forKeyValueEntity(scheduleId)
                            .method(ScheduleEntity::getSchedule)
                            .invoke()
                            .orElse(null);

                    if (schedule != null && schedule.status() == Schedule.Status.ACTIVE) {
                        LocalTime slotTime = schedule.workingHours().startTime();
                        LocalDateTime newDateTime = searchDate.atTime(slotTime);
                        System.out.println("Found available slot with " + doctor.id() + " on " + newDateTime);
                        return Optional.of(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(newDateTime, doctor.id()));
                    }
                }
                searchDate = searchDate.plusDays(1);
            }

            System.out.println("Uh oh, could not find another slot in the " + searchDurationDays + "-day window.");
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("Error finding next available slot: " + e.getMessage());
            return Optional.empty();
        }
    }


    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                .stepTimeout(DeleteScheduleWorkflow::processNextAppointment, Duration.ofSeconds(10))
                .build();
    }
}