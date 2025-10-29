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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component(id = "delete-schedule")
public class DeleteScheduleWorkflow extends Workflow<DeleteScheduleWorkflow.DeleteScheduleState> {

    private final ComponentClient componentClient;
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    public DeleteScheduleWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record DeleteScheduleState(String doctorId, LocalDate date, List<String> appointmentsToCancel) {}
    public record DeleteScheduleCommand(String doctorId, LocalDate date) {}

    /**
     * Step 1: Start the workflow.
     */
    public Effect<Done> start(DeleteScheduleCommand cmd) {
        if (currentState() != null) {
            return effects().error("Workflow already running for this schedule deletion.");
        }
        var state = new DeleteScheduleState(cmd.doctorId, cmd.date, List.of());
        return effects()
                .updateState(state)
                .transitionTo(DeleteScheduleWorkflow::blockSchedule) // Move to step 2
                .thenReply(Done.getInstance());
    }

    /**
     * Step 2: Lock the schedule.
     */
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


    /**
     * Step 3: Process Appointments
     */
    public StepEffect processNextAppointment() {
        var appointmentsToCancel = currentState().appointmentsToCancel();

        if (appointmentsToCancel.isEmpty()) {
            return stepEffects().thenTransitionTo(DeleteScheduleWorkflow::softDeleteSchedule);
        }

        return stepEffects().thenTransitionTo(DeleteScheduleWorkflow::attemptReschedule);
    }

    /**
     * Step 4: Attempt to Reschedule.
     */
    public StepEffect attemptReschedule() {
        var appointmentsToCancel = currentState().appointmentsToCancel();
        var appointmentId = appointmentsToCancel.get(0);
        var remainingAppointments = appointmentsToCancel.stream().skip(1).toList();

        System.out.println("Attempting to reschedule appointment " + appointmentId);
        System.out.println("Remaining appointments size: " + remainingAppointments.size());

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

    /**
     * Step 5: Finish Deletion.
     */
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
        return stepEffects().thenEnd(); // End the workflow
    }


    /**
     * Helper Function
     */
    private Optional<RescheduleAppointmentWorkflow.RescheduleAppointmentCommand> findNextAvailableSlot(Appointment appointment, String urgency) {
        try {
            System.out.println("..........Trying to find a new slot for issue: " + appointment.issue() + "..........");

            var agentSession = commandContext().workflowId() + "-" + appointment.id();
            String speciality = componentClient
                    .forAgent()
                    .inSession(agentSession)
                    .method(DoctorFinderAgent::getSpecialityForIssue)
                    .invoke(appointment.issue())
                    .trim();

            System.out.println("AI determined speciality: " + speciality);

            // Fallback
            if (speciality.isEmpty() || speciality.length() > 50) {
                System.out.println("AI returned invalid speciality, falling back to original doctor's speciality.");
                Doctor originalDoctor = componentClient
                        .forKeyValueEntity(appointment.doctorId())
                        .method(DoctorEntity::getDoctor)
                        .invoke()
                        .orElseThrow(() -> new RuntimeException("Original doctor not found"));
                if (originalDoctor.specialities().isEmpty()) {
                    return Optional.empty();
                }
                speciality = originalDoctor.specialities().getFirst();
            }

            List<DoctorsView.DoctorRow> similarDoctors = componentClient
                    .forView()
                    .method(DoctorsView::findBySpeciality)
                    .invoke(new DoctorsView.FindBySpecialityQuery(speciality))
                    .doctors();

            System.out.println("List of similar doctors in " + speciality + ": " + similarDoctors);

            int searchDurationDays;
            LocalDate searchStartDate = appointment.dateTime().toLocalDate();

            switch (urgency) {
                case "high":
                    searchDurationDays = 7;
                    System.out.println("HIGH urgency: Searching for 7 days starting from today.");
                    break;
                case "medium":
                    searchDurationDays = 7;
                    System.out.println("MEDIUM urgency: Searching for 7 days starting from " + searchStartDate);
                    break;
                case "low":
                default:
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
                        Optional<LocalTime> availableSlotTime = findFirstAvailableSlot(schedule);

                        if (availableSlotTime.isPresent()) {
                            LocalTime slotTime = availableSlotTime.get();
                            LocalDateTime newDateTime = searchDate.atTime(slotTime);
                            System.out.println("Found available slot with " + doctor.id() + " on " + newDateTime);
                            return Optional.of(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(newDateTime, doctor.id()));
                        }
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

    private Optional<LocalTime> findFirstAvailableSlot(Schedule schedule) {
        List<Schedule.TimeSchedule> sortedSlots = schedule.timeSlots().stream()
                .sorted(Comparator.comparing(Schedule.TimeSchedule::startTime))
                .toList();

        LocalTime lastEndTime = schedule.workingHours().startTime();

        for (Schedule.TimeSchedule slot : sortedSlots) {
            LocalTime currentStartTime = slot.startTime();

            if (Duration.between(lastEndTime, currentStartTime).compareTo(DeleteScheduleWorkflow.DEFAULT_DURATION) >= 0) {
                return Optional.of(lastEndTime);
            }

            if (slot.endTime().isAfter(lastEndTime)) {
                lastEndTime = slot.endTime();
            }
        }

        LocalTime workingEndTime = schedule.workingHours().endTime();
        if (Duration.between(lastEndTime, workingEndTime).compareTo(DeleteScheduleWorkflow.DEFAULT_DURATION) >= 0) {
            return Optional.of(lastEndTime);
        }

        return Optional.empty();
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettingsBuilder
                .newBuilder()
                .stepTimeout(DeleteScheduleWorkflow::attemptReschedule, Duration.ofSeconds(30))
                .build();
    }
}