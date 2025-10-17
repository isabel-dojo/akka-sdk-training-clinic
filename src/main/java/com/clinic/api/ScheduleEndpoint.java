package com.clinic.api;

import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.ScheduleEntity;
import com.clinic.domain.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;

@HttpEndpoint("schedules")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ScheduleEndpoint extends AbstractHttpEndpoint {

    private ComponentClient componentClient;
    public ScheduleEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public static final String DOCTOR_ID_HEADER = "doctorId";

    public record WorkingHours(String startTime, String endTime) {}
    public record CreateScheduleRequest(
            WorkingHours workingHours
    ) {}

    @Put("{day}")
    public void createDoctor(String day, CreateScheduleRequest body) {
        var doctorId = requestContext()
                .requestHeader(DOCTOR_ID_HEADER)
                .map(HttpHeader::value)
                .orElseThrow(() -> HttpException.badRequest("Missing doctorId header"));
        LocalDate date = parseDate(day);
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot schedule for past dates");
        }
        var scheduleId = new Schedule.ScheduleId(doctorId, date);
        var workingHours = new Schedule.WorkingHours(
                parseTime(body.workingHours.startTime),
                parseTime(body.workingHours().endTime)
        );

        componentClient
                .forKeyValueEntity(scheduleId.toString())
                .method(ScheduleEntity::createSchedule)
                .invoke(workingHours);
    }

    private LocalDate parseDate(String day) {
        try {
            return LocalDate.parse(day);
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid date format");
        }
    }

    private LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time);
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid time format");
        }
    }
}