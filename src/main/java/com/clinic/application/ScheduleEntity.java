package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.clinic.domain.Schedule;

import java.util.Optional;

@Component(id = "schedule")
public class ScheduleEntity extends KeyValueEntity<Schedule> {
    private Schedule.ScheduleId entityId;

    public ScheduleEntity(KeyValueEntityContext context) {
        this.entityId = Schedule.ScheduleId.fromString(context.entityId());
    }

    public Effect<Done> createSchedule(Schedule.WorkingHours workingHours) {
        if (currentState() != null)
            return effects().error("Schedule already exists");

        var schedule = new Schedule(entityId, workingHours);
        return effects()
                .updateState(schedule)
                .thenReply(Done.getInstance());
    }

    public Effect<Optional<Schedule>> getSchedule() {
        return effects().reply(Optional.ofNullable(currentState()));
    }
}
