package com.airline.agentorder.service;

import com.airline.agentorder.dto.AgentTraceResponse;
import com.airline.agentorder.model.AgentTraceEvent;
import com.airline.agentorder.model.AgentTraceRun;
import com.airline.agentorder.model.AgentTraceSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentMonitorService {

    private final Map<String, AgentTraceSession> traceStore = new ConcurrentHashMap<>();

    public String startRun(String sessionId, String userMessage) {
        AgentTraceSession session = traceStore.computeIfAbsent(sessionId, this::newSessionTrace);
        AgentTraceRun run = new AgentTraceRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setUserMessage(userMessage);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        session.getRuns().add(0, run);
        trimRuns(session);
        session.setUpdatedAt(LocalDateTime.now());
        return run.getRunId();
    }

    public void completeRun(String sessionId, String runId, String assistantReply) {
        AgentTraceRun run = getOrCreateRun(sessionId, runId);
        run.setAssistantReply(assistantReply);
        run.setStatus("COMPLETED");
        if (run.getStartedAt() == null) {
            run.setStartedAt(LocalDateTime.now());
        }
        run.setFinishedAt(LocalDateTime.now());
        traceStore.get(sessionId).setUpdatedAt(LocalDateTime.now());
    }

    public void failRun(String sessionId, String runId, String errorMessage) {
        AgentTraceRun run = getOrCreateRun(sessionId, runId);
        run.setAssistantReply(errorMessage);
        run.setStatus("FAILED");
        if (run.getStartedAt() == null) {
            run.setStartedAt(LocalDateTime.now());
        }
        run.setFinishedAt(LocalDateTime.now());
        traceStore.get(sessionId).setUpdatedAt(LocalDateTime.now());
    }

    public void recordModelCall(String sessionId, String runId, String summary, long durationMs) {
        AgentTraceSession session = traceStore.computeIfAbsent(sessionId, this::newSessionTrace);
        AgentTraceRun run = getOrCreateRun(session, runId);
        AgentTraceEvent event = buildEvent("MODEL", "chat_model", "SUCCESS", summary, durationMs);
        run.getEvents().add(event);
        run.setModelCallCount(run.getModelCallCount() + 1);
        session.setTotalModelCalls(session.getTotalModelCalls() + 1);
        session.setUpdatedAt(LocalDateTime.now());
    }

    public void recordToolCall(String sessionId, String runId, String toolName, String status, String summary, long durationMs) {
        AgentTraceSession session = traceStore.computeIfAbsent(sessionId, this::newSessionTrace);
        AgentTraceRun run = getOrCreateRun(session, runId);
        AgentTraceEvent event = buildEvent("TOOL", toolName, status, summary, durationMs);
        run.getEvents().add(event);
        run.setToolCallCount(run.getToolCallCount() + 1);
        session.setTotalToolCalls(session.getTotalToolCalls() + 1);
        session.setUpdatedAt(LocalDateTime.now());
    }

    public void recordMemoryOperation(String sessionId, String runId, String action, String summary, long durationMs) {
        AgentTraceSession session = traceStore.computeIfAbsent(sessionId, this::newSessionTrace);
        AgentTraceRun run = getOrCreateRun(session, runId);
        AgentTraceEvent event = buildEvent("MEMORY", action, "SUCCESS", summary, durationMs);
        run.getEvents().add(event);
        run.setMemoryOpCount(run.getMemoryOpCount() + 1);
        session.setTotalMemoryOps(session.getTotalMemoryOps() + 1);
        session.setUpdatedAt(LocalDateTime.now());
    }

    public AgentTraceResponse getTrace(String sessionId) {
        AgentTraceSession session = traceStore.get(sessionId);
        if (session == null) {
            throw new NoSuchElementException("trace not found");
        }

        AgentTraceResponse response = new AgentTraceResponse();
        response.setSessionId(session.getSessionId());
        response.setTotalModelCalls(session.getTotalModelCalls());
        response.setTotalToolCalls(session.getTotalToolCalls());
        response.setTotalMemoryOps(session.getTotalMemoryOps());
        response.setUpdatedAt(session.getUpdatedAt());
        response.setRuns(copyRuns(session.getRuns()));
        return response;
    }

    private AgentTraceSession newSessionTrace(String sessionId) {
        AgentTraceSession session = new AgentTraceSession();
        session.setSessionId(sessionId);
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    private AgentTraceRun findRun(String sessionId, String runId) {
        AgentTraceSession session = traceStore.get(sessionId);
        if (session == null) {
            throw new NoSuchElementException("trace session not found");
        }
        return session.getRuns().stream()
            .filter(item -> item.getRunId().equals(runId))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("trace run not found"));
    }

    private AgentTraceRun getOrCreateRun(String sessionId, String runId) {
        AgentTraceSession session = traceStore.computeIfAbsent(sessionId, this::newSessionTrace);
        return getOrCreateRun(session, runId);
    }

    private AgentTraceRun getOrCreateRun(AgentTraceSession session, String runId) {
        return session.getRuns().stream()
            .filter(item -> item.getRunId().equals(runId))
            .findFirst()
            .orElseGet(() -> {
                AgentTraceRun run = new AgentTraceRun();
                run.setRunId(runId);
                run.setUserMessage("[auto-created trace run]");
                run.setStatus("RUNNING");
                run.setStartedAt(LocalDateTime.now());
                session.getRuns().add(0, run);
                trimRuns(session);
                session.setUpdatedAt(LocalDateTime.now());
                return run;
            });
    }

    private AgentTraceEvent buildEvent(String type, String name, String status, String summary, long durationMs) {
        AgentTraceEvent event = new AgentTraceEvent();
        event.setType(type);
        event.setName(name);
        event.setStatus(status);
        event.setSummary(summary);
        event.setDurationMs(durationMs);
        event.setTime(LocalDateTime.now());
        return event;
    }

    private void trimRuns(AgentTraceSession session) {
        if (session.getRuns().size() > 20) {
            session.getRuns().subList(20, session.getRuns().size()).clear();
        }
    }

    private List<AgentTraceRun> copyRuns(List<AgentTraceRun> sourceRuns) {
        List<AgentTraceRun> result = new ArrayList<>();
        for (AgentTraceRun sourceRun : sourceRuns) {
            AgentTraceRun targetRun = new AgentTraceRun();
            targetRun.setRunId(sourceRun.getRunId());
            targetRun.setUserMessage(sourceRun.getUserMessage());
            targetRun.setAssistantReply(sourceRun.getAssistantReply());
            targetRun.setStatus(sourceRun.getStatus());
            targetRun.setModelCallCount(sourceRun.getModelCallCount());
            targetRun.setToolCallCount(sourceRun.getToolCallCount());
            targetRun.setMemoryOpCount(sourceRun.getMemoryOpCount());
            targetRun.setStartedAt(sourceRun.getStartedAt());
            targetRun.setFinishedAt(sourceRun.getFinishedAt());
            targetRun.getEvents().addAll(sourceRun.getEvents());
            result.add(targetRun);
        }
        return result;
    }
}
