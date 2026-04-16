package com.airline.agentorder.agent;

import com.airline.agentorder.service.AgentMonitorService;
import com.airline.agentorder.service.SessionContextHolder;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class AgentModelTraceInterceptor extends ModelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AgentModelTraceInterceptor.class);

    private final AgentMonitorService agentMonitorService;

    public AgentModelTraceInterceptor(AgentMonitorService agentMonitorService) {
        this.agentMonitorService = agentMonitorService;
    }

    @Override
    public String getName() {
        return "agent_model_trace";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        long start = System.currentTimeMillis();
        ModelResponse response = handler.call(request);
        long durationMs = System.currentTimeMillis() - start;

        String sessionId = SessionContextHolder.getSessionId();
       String runId = SessionContextHolder.getRunId();
        if (sessionId != null && runId != null) {
            String summary = "messages=" + request.getMessages().size()
                + ", tools=" + request.getTools()
                + ", tail=" + summarizeTail(request);
            try {
                agentMonitorService.recordModelCall(sessionId, runId, summary, durationMs);
            } catch (RuntimeException ex) {
                log.warn("skip model trace record: {}", ex.getMessage());
            }
        }
        return response;
    }

    private String summarizeTail(ModelRequest request) {
        return request.getMessages().stream()
            .skip(Math.max(0, request.getMessages().size() - 2L))
            .map(Message::getText)
            .map(text -> text == null ? "" : text.replaceAll("\\s+", " ").trim())
            .map(text -> text.length() > 60 ? text.substring(0, 60) + "..." : text)
            .collect(Collectors.joining(" | "));
    }
}
