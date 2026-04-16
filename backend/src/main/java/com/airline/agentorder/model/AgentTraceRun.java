package com.airline.agentorder.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgentTraceRun {

    private String runId;
    private String userMessage;
    private String assistantReply;
    private String status;
    private int modelCallCount;
    private int toolCallCount;
    private int memoryOpCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private final List<AgentTraceEvent> events = new ArrayList<>();

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAssistantReply() {
        return assistantReply;
    }

    public void setAssistantReply(String assistantReply) {
        this.assistantReply = assistantReply;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getModelCallCount() {
        return modelCallCount;
    }

    public void setModelCallCount(int modelCallCount) {
        this.modelCallCount = modelCallCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public int getMemoryOpCount() {
        return memoryOpCount;
    }

    public void setMemoryOpCount(int memoryOpCount) {
        this.memoryOpCount = memoryOpCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public List<AgentTraceEvent> getEvents() {
        return events;
    }
}
