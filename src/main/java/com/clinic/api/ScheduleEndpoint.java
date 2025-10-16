package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;

import java.time.LocalDate;

@HttpEndpoint("schedules")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ScheduleEndpoint extends AbstractHttpEndpoint {

    public static final String DOCTOR_ID_HEADER = "doctorId";

    public record Availability(String startTime, String endTime) {}
    public record CreateScheduleRequest(
            Availability availability
    ) {}

    @Put("{day}")
    public void createDoctor(String day, CreateScheduleRequest body) {
        var doctorId = requestContext()
                .requestHeader(DOCTOR_ID_HEADER)
                .map(String::valueOf)
                .orElseThrow(() -> HttpException.badRequest("Missing doctorId header"));
        LocalDate date = parseDate(day);
        if (date.isBefore(LocalDate.now())) {
            throw HttpException.badRequest("Cannot schedule for past dates");
        }
        System.out.println("Received schedule for day: " + day + " doctor: " + doctorId + " availability: " + body);
    }

    private LocalDate parseDate(String day) {
        try {
            return LocalDate.parse(day);
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid date format");
        }
    }
}