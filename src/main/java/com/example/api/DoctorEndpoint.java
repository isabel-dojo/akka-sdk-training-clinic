package com.example.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;

import java.util.List;
import java.util.Optional;

@HttpEndpoint("doctors")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class DoctorEndpoint extends AbstractHttpEndpoint {

    public record CreateDoctorRequest(
            String firstName,
            String lastName,
            List<String> specialities,
            String description,
            Optional<Contact> contact
    ) {
    }

    public record Contact(Optional<String> phone, Optional<String> email) {
    }

    @Post("{id}")
    public void createDoctor(String id, CreateDoctorRequest body) {
        if (id.equals("house")) {
            throw HttpException.error(StatusCodes.CONFLICT, "Doctor already exists");
        }
        System.out.println("Received doctor: " + body);
    }

    public record DoctorSummary(String id, String name, List<String> specialities) {
    }

    @Get
    public List<DoctorSummary> getDoctors() {
        return List.of(new DoctorSummary("house", "Gregory House", List.of("Cardiologist", "Nephrology")));
    }

    public record DoctorDetails(
            String id,
            String firstName,
            String lastName,
            List<String> specialities,
            String description,
            Optional<Contact> contact
    ) {
    }

    @Get("{id}")
    public DoctorDetails getDoctor(String id) {
        if (id.equals("house")) {
            Contact contact = new Contact(Optional.of("+1 234 567 8901"), Optional.empty());
            return new DoctorDetails(id, "Gregory", "House", List.of("Cardiologist", "Nephrology"), "Doctor of Cardiology", Optional.of(contact));
        } else {
            throw HttpException.notFound();
        }
    }
}