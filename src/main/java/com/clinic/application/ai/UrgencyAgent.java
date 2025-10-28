package com.clinic.application.ai;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

@Component(id = "urgency-agent")
public class UrgencyAgent extends Agent{

    private static final String SYSTEM_MESSAGE = """
            You are an AI triage assistant responsible for evaluating patient-reported medical issues.
            Your primary task is to analyze the symptoms, context, and details provided in the patient's message to determine its urgency.
            Based on your analysis, you must assign an urgency level that dictates the required response priority.
            You must classify the situation into one of three specific categories: "high", "medium", or "low".
            Your final output for this assessment must be only one of these three exact terms.
            """;

    public Effect<String> urgency(String issue) {
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(issue)
                .thenReply();
    }


}
