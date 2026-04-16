package com.airline.agentorder.dto;

public class CreateSessionResponse {

    private String sessionId;
    private String welcomeMessage;

    public CreateSessionResponse() {
    }

    public CreateSessionResponse(String sessionId, String welcomeMessage) {
        this.sessionId = sessionId;
        this.welcomeMessage = welcomeMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }
}
