package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.AppointmentEntity;
import com.clinic.application.ScheduleAppointmentWorkflow;
import com.clinic.application.RescheduleAppointmentWorkflow;
import com.clinic.domain.Appointment;

import java.time.LocalDate;
import java.util.UUID;

import static com.clinic.api.common.Validation.parseDate;
import static com.clinic.api.common.Validation.parseTime;

@HttpEndpoint("appointments")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class AppointmentEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public AppointmentEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record CreateAppointmentRequest(String doctorId, String date, String startTime, String issue, String patientId) {
    }

    public record CreateAppointmentResponse(String id) {
    }

    @Post
    public CreateAppointmentResponse scheduleAppointment(CreateAppointmentRequest body) {
        LocalDate date = parseDate(body.date);
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot schedule an appointment for past dates");
        }
        var appointmentId = UUID.randomUUID().toString();
        componentClient
                .forWorkflow(appointmentId)
                .method(ScheduleAppointmentWorkflow::schedule)
                .invoke(new ScheduleAppointmentWorkflow.ScheduleAppointmentCommand(date.atTime(parseTime(body.startTime)), body.doctorId, body.patientId, body.issue));

        return new CreateAppointmentResponse(appointmentId);
    }

    public record RescheduleAppointmentRequest(String doctorId, String date, String startTime) {
    }

    @Put("{id}")
    public void reschedule(String id, RescheduleAppointmentRequest body) {
        componentClient
                .forWorkflow(id)
                .method(RescheduleAppointmentWorkflow::startRescheduleAppointment)
                .invoke(new RescheduleAppointmentWorkflow.RescheduleAppointmentCommand(parseDate(body.date).atTime(parseTime(body.startTime)), body.doctorId));
    }

    public record AddNotesRequest(String notes) {
    }

    @Put("{id}/notes")
    public void addNotes(String id, AddNotesRequest body) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::addNotes)
                .invoke(body.notes());
    }

    public record AddPrescriptionRequest(String prescription) {
    }

    @Post("{id}/prescriptions")
    public void addPrescription(String id, AddPrescriptionRequest body) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::addPrescription)
                .invoke(body.prescription());
    }

    @Put("{id}/complete")
    public void complete(String id) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::complete)
                .invoke();
    }

    @Put("{id}/cancel")
    public void cancel(String id) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::cancel)
                .invoke();
    }

    @Put("{id}/missed")
    public void missed(String id) {
        componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::markAsMissed)
                .invoke();
    }

    @Get("{id}")
    public Appointment getAppointment(String id) {
        return componentClient
                .forEventSourcedEntity(id)
                .method(AppointmentEntity::getAppointment)
                .invoke()
                .orElseThrow(HttpException::notFound);
    }
}
