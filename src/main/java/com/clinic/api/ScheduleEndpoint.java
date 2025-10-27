package com.clinic.api;

import akka.http.javadsl.model.HttpHeader;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.DeleteScheduleWorkflow;
import com.clinic.application.ScheduleEntity;
import com.clinic.application.SchedulesByDoctorView;
import com.clinic.domain.Schedule;

import java.time.LocalDate;
import java.util.List;

import static com.clinic.api.common.Validation.parseDate;
import static com.clinic.api.common.Validation.parseTime;

@HttpEndpoint("schedules")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ScheduleEndpoint extends AbstractHttpEndpoint {

    private ComponentClient componentClient;

    public ScheduleEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public static final String DOCTOR_ID_HEADER = "doctorId";

    public record WorkingHours(String startTime, String endTime) {
    }

    public record CreateScheduleRequest(WorkingHours workingHours) {
    }

    @Put("{day}")
    public void upsertSchedule(String day, CreateScheduleRequest body) {
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

    @Delete("{day}")
    public void deleteSchedule(String day) {
        var doctorId = requestContext()
                .requestHeader(DOCTOR_ID_HEADER)
                .map(HttpHeader::value)
                .orElseThrow(() -> HttpException.badRequest("Missing doctorId header"));
        LocalDate date = parseDate(day);

        var workflowId = "delete-schedule-" + doctorId + "-" + day;

        componentClient
                .forWorkflow(workflowId)
                .method(DeleteScheduleWorkflow::start)
                .invoke(new DeleteScheduleWorkflow.DeleteScheduleCommand(doctorId, date));
    }
}