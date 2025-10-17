package com.clinic.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Schedule(ScheduleId id, WorkingHours workingHours, List<Appointment> appointments) {

    private static final Duration MIN_DURATION = Duration.ofMinutes(5);

    public Schedule {
        var isInWorkingHours = appointments
                .stream()
                .allMatch(workingHours::isInWorkingHours);
        if (!isInWorkingHours)
            throw new IllegalArgumentException("Appointment is not in working hours");

        var isOverlapping = appointments
                .stream()
                .anyMatch(slot -> appointments.stream().anyMatch(otherSlot -> slot != otherSlot && slot.overlaps(otherSlot)));
        if (isOverlapping)
            throw new IllegalArgumentException("Appointment overlaps with another appointment");
    }

    public Schedule(ScheduleId id, WorkingHours workingHours) {
        this(id, workingHours, List.of());
    }

    public record ScheduleId(String doctorId, LocalDate date) {}
    /**
     * @param startTime inclusive
     * @param endTime exclusive
     */
    public record WorkingHours(LocalTime startTime, LocalTime endTime) {
        public WorkingHours {
           if (startTime.isAfter(endTime)) {
               throw new IllegalArgumentException("Start time must be before end time");
           }
        }

        public boolean isInWorkingHours(Appointment timeSlot) {
            return !timeSlot.startTime().isBefore(startTime()) && !timeSlot.endTime().isAfter(endTime());
        }
    }

    /**
     * @param startTime inclusive
     * @param endTime exclusive
     */
    public record Appointment(LocalTime startTime, LocalTime endTime) {
        public Appointment {
            if (startTime.isAfter(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
            if (Duration.between(startTime, endTime).compareTo(MIN_DURATION) < 0) {
                throw new IllegalArgumentException("TimeSlot duration must be at least " + MIN_DURATION);
            }
        }

        public boolean overlaps(Appointment other) {
            return other.startTime().isBefore(endTime()) && other.endTime().isAfter(startTime());
        }
    }

    public Schedule scheduleAppointment(LocalTime startTime, Duration duration) {
        var newTimeSlot = new Appointment(startTime, startTime.plus(duration));
        var newSlots = new ArrayList<>(appointments); //copy original slots
        newSlots.add(newTimeSlot); //add a new time slot to the copy
        return new Schedule(id, workingHours, Collections.unmodifiableList(newSlots));
    }
}

