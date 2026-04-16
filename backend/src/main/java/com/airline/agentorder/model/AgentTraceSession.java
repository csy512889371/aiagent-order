package com.airline.agentorder.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgentTraceSession {

    private String sessionId;
    private int totalModelCalls;
    private int totalToolCalls;
    private int totalMemoryOps;
    private LocalDateTime updatedAt;
    private final List<AgentTraceRun> runs = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getTotalModelCalls() {
        return totalModelCalls;
    }

    public void setTotalModelCalls(int totalModelCalls) {
        this.totalModelCalls = totalModelCalls;
    }

    public int getTotalToolCalls() {
        return totalToolCalls;
    }

    public void setTotalToolCalls(int totalToolCalls) {
        this.totalToolCalls = totalToolCalls;
    }

    public int getTotalMemoryOps() {
        return totalMemoryOps;
    }

    public void setTotalMemoryOps(int totalMemoryOps) {
        this.totalMemoryOps = totalMemoryOps;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<AgentTraceRun> getRuns() {
        return runs;
    }
}
