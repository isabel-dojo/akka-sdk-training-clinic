package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.ScheduleEntity;
import com.clinic.domain.Schedule;

import java.time.Duration;
import java.time.LocalDate;

import static com.clinic.api.common.Validation.parseDate;
import static com.clinic.api.common.Validation.parseTime;

@HttpEndpoint("appointments")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class AppointmentEndpoint extends AbstractHttpEndpoint {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(30);

    private final ComponentClient componentClient;

    public AppointmentEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record CreateAppointmentRequest(String doctorId, String date, String startTime) {
    }

    @Post
    public void scheduleAppointment(CreateAppointmentRequest body) {
        LocalDate date = parseDate(body.date);
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot schedule an appointment for past dates");
        }
        var scheduleId = new Schedule.ScheduleId(body.doctorId, date);
        componentClient
                .forKeyValueEntity(scheduleId.toString())
                .method(ScheduleEntity::scheduleAppointment)
                .invoke(new ScheduleEntity.ScheduleAppointmentData(parseTime(body.startTime), DEFAULT_DURATION));
    }
}
