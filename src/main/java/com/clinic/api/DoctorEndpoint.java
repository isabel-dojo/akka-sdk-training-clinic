package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import com.clinic.application.DoctorEntity; // New import
import com.clinic.application.DoctorsView; // New import
import com.clinic.application.SchedulesByDoctorView;
import com.clinic.domain.Doctor; // New import

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors; // New import

@HttpEndpoint("doctors")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class DoctorEndpoint extends AbstractHttpEndpoint {

    private ComponentClient componentClient;

    public DoctorEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

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

    // Helper method to convert Domain Contact to API Contact
    private Contact toApiContact(Doctor.Contact domainContact) {
        return new Contact(domainContact.phone(), domainContact.email());
    }

    @Post("{id}")
    public void createDoctor(String id, CreateDoctorRequest body) {
        // Map API Contact record to Domain Contact record
        var domainContact = body.contact.map(c -> new Doctor.Contact(c.phone(), c.email()));

        var cmd = new DoctorEntity.CreateDoctorCommand(
                body.firstName,
                body.lastName,
                body.specialities,
                body.description,
                domainContact
        );

        // Call the entity to create the doctor
        componentClient
                .forKeyValueEntity(id)
                .method(DoctorEntity::createDoctor)
                .invoke(cmd);
    }

    public record DoctorSummary(String id, String name, List<String> specialities) {
    }

    @Get
    public List<DoctorSummary> getDoctors() {
        // Call the view to get all doctors
        List<DoctorsView.DoctorRow> rows = componentClient
                .forView()
                .method(DoctorsView::getAllDoctors)
                .invoke()
                .doctors();

        // Map view rows to API summary
        return rows.stream()
                .map(row -> new DoctorSummary(
                        row.id(),
                        row.firstName() + " " + row.lastName(),
                        row.specialities()
                ))
                .collect(Collectors.toList());
    }

    // New endpoint to find by speciality
    @Get("speciality/{speciality}")
    public List<DoctorSummary> getDoctorsBySpeciality(String speciality) {

        // --- THIS IS THE CORRECTED CALL ---
        // Wrap the 'speciality' string in the new record
        List<DoctorsView.DoctorRow> rows = componentClient
                .forView()
                .method(DoctorsView::findBySpeciality)
                .invoke(new DoctorsView.FindBySpecialityQuery(speciality)) // Pass the new record
                .doctors();

        // Map rows to summary
        return rows.stream()
                .map(row -> new DoctorSummary(
                        row.id(),
                        row.firstName() + " " + row.lastName(),
                        row.specialities()
                ))
                .collect(Collectors.toList());
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
        // Call the entity to get a specific doctor
        Doctor doctor = componentClient
                .forKeyValueEntity(id)
                .method(DoctorEntity::getDoctor)
                .invoke()
                .orElseThrow(HttpException::notFound);

        // Map domain Doctor to API DoctorDetails
        return new DoctorDetails(
                doctor.id(),
                doctor.firstName(),
                doctor.lastName(),
                doctor.specialities(),
                doctor.description(),
                doctor.contact().map(this::toApiContact) // convert domain contact to api contact
        );
    }

    @Get("{doctorId}/schedules")
    public List<SchedulesByDoctorView.ScheduleSummary> getSchedulesByDoctor(String doctorId) {
        // This endpoint remains unchanged
        return componentClient.forView().method(SchedulesByDoctorView::getSummaries).invoke(new SchedulesByDoctorView.FindScheduleSummary(doctorId, "2025-10-20", "2025-10-30")).schedules();
    }
}