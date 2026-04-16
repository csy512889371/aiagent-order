package com.airline.agentorder.dto;

import com.airline.agentorder.model.AgentTraceRun;

import java.time.LocalDateTime;
import java.util.List;

public class AgentTraceResponse {

    private String sessionId;
    private int totalModelCalls;
    private int totalToolCalls;
    private int totalMemoryOps;
    private LocalDateTime updatedAt;
    private List<AgentTraceRun> runs;

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

    public void setRuns(List<AgentTraceRun> runs) {
        this.runs = runs;
    }
}
