package com.clinic.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public record Schedule(ScheduleId id, WorkingHours workingHours, List<TimeSchedule> timeSlots) {

    private static final Duration MIN_DURATION = Duration.ofMinutes(5);

    public Schedule {
        var isInWorkingHours = timeSlots
                .stream()
                .allMatch(workingHours::isInWorkingHours);
        if (!isInWorkingHours)
            throw new IllegalArgumentException("Appointment is not in working hours");

        var isOverlapping = timeSlots
                .stream()
                .anyMatch(slot -> timeSlots.stream().anyMatch(otherSlot -> slot != otherSlot && slot.overlaps(otherSlot)));
        if (isOverlapping)
            throw new IllegalArgumentException("Appointment overlaps with another appointment");
    }

    public Schedule(ScheduleId id, WorkingHours workingHours) {
        this(id, workingHours, List.of());
    }

    public record ScheduleId(String doctorId, LocalDate date) {
        public String toString() {
            return doctorId + ":" + date.toString();
        }

        public static ScheduleId fromString(String id) {
            var splitted = id.split(":");
            var doctorId = splitted[0];
            var date = LocalDate.parse(splitted[1]);
            return new ScheduleId(doctorId, date);
        }
    }

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

        public boolean isInWorkingHours(TimeSchedule timeSlot) {
            return !timeSlot.startTime().isBefore(startTime()) && !timeSlot.endTime().isAfter(endTime());
        }
    }

    /**
     * @param startTime inclusive
     * @param endTime exclusive
     */
    public record TimeSchedule(LocalTime startTime, LocalTime endTime, String appointmentId) {
        public TimeSchedule {
            if (startTime.isAfter(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
            if (Duration.between(startTime, endTime).compareTo(MIN_DURATION) < 0) {
                throw new IllegalArgumentException("TimeSlot duration must be at least " + MIN_DURATION);
            }
        }

        public boolean overlaps(TimeSchedule other) {
            return other.startTime().isBefore(endTime()) && other.endTime().isAfter(startTime());
        }
    }

    public Schedule scheduleAppointment(LocalTime startTime, Duration duration, String appointmentId) {
        var newTimeSlot = new TimeSchedule(startTime, startTime.plus(duration), appointmentId);
        var newSlots = new ArrayList<>(timeSlots); //copy original slots
        newSlots.add(newTimeSlot); //add a new time slot to the copy
        return new Schedule(id, workingHours, Collections.unmodifiableList(newSlots));
    }

    public Schedule removeTimeSlot(String appointmentId, LocalTime startTime) {
        var newSlots = timeSlots
                .stream()
                .filter(slot -> !(slot.appointmentId().equals(appointmentId) && slot.startTime().equals(startTime)))
                .collect(Collectors.toList());

        if (newSlots.size() == timeSlots.size()) {
            throw new IllegalArgumentException("TimeSlot has not been removed");
        }

        return new Schedule(id, workingHours, Collections.unmodifiableList(newSlots));
    }
}

