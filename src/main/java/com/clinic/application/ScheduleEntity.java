package com.clinic.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.clinic.domain.Schedule;

@ComponentId("schedule")
public class ScheduleEntity extends KeyValueEntity<Schedule> {
    private Schedule.ScheduleId entityId;

    public ScheduleEntity(KeyValueEntityContext context) {
        this.entityId = Schedule.ScheduleId.fromString(context.entityId());
    }

    public Effect<Done> createSchedule(Schedule.WorkingHours workingHours) {
        var schedule = new Schedule(entityId, workingHours);
        return effects()
                .updateState(schedule)
                .thenReply(Done.getInstance());
    }
}
