package com.airline.agentorder.agent;

import com.airline.agentorder.service.AgentMonitorService;
import com.airline.agentorder.service.SessionContextHolder;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

public class MonitoringMemorySaver implements BaseCheckpointSaver {

    private static final Logger log = LoggerFactory.getLogger(MonitoringMemorySaver.class);

    private final MemorySaver delegate = new MemorySaver();
    private final AgentMonitorService agentMonitorService;

    public MonitoringMemorySaver(AgentMonitorService agentMonitorService) {
        this.agentMonitorService = agentMonitorService;
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        long start = System.currentTimeMillis();
        Optional<Checkpoint> checkpoint = delegate.get(config);
        long durationMs = System.currentTimeMillis() - start;
        record(config, "get", "checkpointId=" + config.checkPointId().orElse("latest")
            + ", hit=" + checkpoint.isPresent(), durationMs);
        return checkpoint;
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        long start = System.currentTimeMillis();
        RunnableConfig result = delegate.put(config, checkpoint);
        long durationMs = System.currentTimeMillis() - start;
        record(config, "put", "checkpointId=" + checkpoint.getId(), durationMs);
        return result;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        long start = System.currentTimeMillis();
        Collection<Checkpoint> checkpoints = delegate.list(config);
        long durationMs = System.currentTimeMillis() - start;
        record(config, "list", "count=" + checkpoints.size(), durationMs);
        return checkpoints;
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        long start = System.currentTimeMillis();
        Tag tag = delegate.release(config);
        long durationMs = System.currentTimeMillis() - start;
        record(config, "release", "released=true", durationMs);
        return tag;
    }

    private void record(RunnableConfig config, String action, String summary, long durationMs) {
        String sessionId = config.threadId().orElse(SessionContextHolder.getSessionId());
        String runId = SessionContextHolder.getRunId();
        if (sessionId != null && runId != null) {
            try {
                agentMonitorService.recordMemoryOperation(sessionId, runId, action, summary, durationMs);
            } catch (RuntimeException ex) {
                log.warn("skip memory trace record: {}", ex.getMessage());
            }
        }
        log.info("memory-saver action={} sessionId={} runId={} summary={} durationMs={}",
            action, sessionId, runId, summary, durationMs);
    }
}
