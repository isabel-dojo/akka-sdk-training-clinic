package com.clinic.application.ai;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import com.clinic.domain.Doctor;

import java.util.List;
import java.util.Optional;

@Component(id = "doctor-finder-agent")
public class DoctorFinderAgent extends Agent {

    private static final String SYSTEM_MESSAGE = """
            You are an AI doctor finder. Your primary task is to find a suitable doctor for a patient with a given medical condition.
            """;

    public Effect<String> findDoctor(String issue) {
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(issue)
                .thenReply();
    }

    @FunctionTool(name = "get-all-doctors", description = "Returns all available doctors")
    public List<Doctor> getAllDoctors() {
        return List.of(
                new Doctor("house", "Gregory", "House", List.of("Cardiologist"), "", Optional.empty()),
                new Doctor("adams", "Adams", "Tom", List.of("General Practitioner"), "", Optional.empty())
        );
    }
}
