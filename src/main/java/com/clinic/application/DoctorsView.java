package com.clinic.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.clinic.domain.Doctor;

import java.util.List;
import java.util.Optional;

@Component(id = "doctors-view")
public class DoctorsView extends View {

    public record DoctorRow(
            String id,
            String firstName,
            String lastName,
            List<String> specialities,
            String description,
            Optional<Doctor.Contact> contact
    ) {}

    @Table("doctors")
    @Consume.FromKeyValueEntity(DoctorEntity.class)
    public static class Updater extends TableUpdater<DoctorRow> {
        public Effect<DoctorRow> onChange(Doctor doctor) {
            var row = new DoctorRow(
                    doctor.id(),
                    doctor.firstName(),
                    doctor.lastName(),
                    doctor.specialities(),
                    doctor.description(),
                    doctor.contact()
            );
            return effects().updateRow(row);
        }
    }

    public record DoctorRows(List<DoctorRow> doctors) {}

    @Query("SELECT * AS doctors FROM doctors")
    public QueryEffect<DoctorRows> getAllDoctors() {
        return queryResult();
    }

    public record FindBySpecialityQuery(String speciality) {}

    @Query("SELECT * AS doctors FROM doctors WHERE :speciality = ANY(specialities)")
    public QueryEffect<DoctorRows> findBySpeciality(FindBySpecialityQuery query) {
        return queryResult();
    }

}
