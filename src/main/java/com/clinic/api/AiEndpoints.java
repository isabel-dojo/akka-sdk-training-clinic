package com.clinic.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.clinic.application.ai.ChatAgent;
import com.clinic.application.ai.DoctorFinderAgent;
import com.clinic.application.ai.UrgencyAgent;

import java.util.UUID;

@HttpEndpoint("ai")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class AiEndpoints extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public AiEndpoints(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Put("/ask")
    public String urgency(String issue) {
        var session = UUID.randomUUID().toString();
        return componentClient
                .forAgent()
                .inSession(session)
                .method(UrgencyAgent::urgency)
                .invoke(issue);
    }

    @Put("/chat")
    public String chat(String issue) {
        var session = requestContext().queryParams().getString("session").orElse(UUID.randomUUID().toString());
        return componentClient
                .forAgent()
                .inSession(session)
                .method(ChatAgent::ask)
                .invoke(issue);
    }

    @Put("/find-doctor")
    public String findDoctor(String issue) {
        var session = requestContext().queryParams().getString("session").orElse(UUID.randomUUID().toString());
        return componentClient
                .forAgent()
                .inSession(session)
                .method(DoctorFinderAgent::findDoctor)
                .invoke(issue);
    }
}
