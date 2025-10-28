package com.clinic.application.ai;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import com.clinic.application.DoctorsView;
import com.clinic.domain.Doctor;

import java.util.List;
import java.util.Optional;

@Component(id = "doctor-finder-agent")
public class DoctorFinderAgent extends Agent {

    private final ComponentClient componentClient;

    public DoctorFinderAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }


    private static final String SYSTEM_MESSAGE = """
            You are an AI doctor finder. Your primary task is to find a suitable doctor for a patient with a given medical condition.
            First, determine a likely medical speciality based on the user's issue.
            Then, use the 'find_doctors_by_speciality' tool to find doctors for that speciality.
            Finally, present the results to the user.
            """;

    public Effect<String> findDoctor(String issue) {
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(issue)
                .thenReply();
    }

    /*@FunctionTool(name = "get-all-doctors", description = "Returns all available doctors")
    public List<Doctor> getAllDoctors() {
        return List.of(
                new Doctor("house", "Gregory", "House", List.of("Cardiologist"), "", Optional.empty()),
                new Doctor("adams", "Adams", "Tom", List.of("General Practitioner"), "", Optional.empty())
        );
    }*/

    @FunctionTool(name = "find-doctors-by-speciality", description = "Returns all available doctors for a given speciality")
    public List<DoctorsView.DoctorRow> findDoctorsBySpeciality(String speciality) {
        return componentClient
                .forView()
                .method(DoctorsView::findBySpeciality)
                .invoke(new DoctorsView.FindBySpecialityQuery(speciality))
                .doctors();
    }

    private static final String WORKFLOW_SYSTEM_MESSAGE = """
            You are an AI assistant. Your job is to determine the single best medical speciality (e.g., 'Cardiologist', 'General Practitioner') required for a given medical issue.
            Respond *only* with the name of the speciality. Do not add any other text.
            """;

    public Effect<String> getSpecialityForIssue(String issue) {
        return effects()
                .systemMessage(WORKFLOW_SYSTEM_MESSAGE)
                .userMessage(issue)
                .thenReply();
    }
}
