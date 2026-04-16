package com.airline.agentorder.agent;

import com.airline.agentorder.service.AgentMonitorService;
import com.airline.agentorder.service.SessionContextHolder;
import com.airline.agentorder.tool.ToolSessionSupport;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentToolTraceInterceptor extends ToolInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolTraceInterceptor.class);

    private final AgentMonitorService agentMonitorService;

    public AgentToolTraceInterceptor(AgentMonitorService agentMonitorService) {
        this.agentMonitorService = agentMonitorService;
    }

    @Override
    public String getName() {
        return "agent_tool_trace";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        ToolCallRequest requestWithContext = augmentContext(request);
        long start = System.currentTimeMillis();
        ToolCallResponse response = handler.call(requestWithContext);
        long durationMs = System.currentTimeMillis() - start;

        String sessionId = (String) requestWithContext.getContext().get(ToolSessionSupport.SESSION_ID_KEY);
        String runId = (String) requestWithContext.getContext().get(ToolSessionSupport.RUN_ID_KEY);
        if (sessionId != null && runId != null) {
            String status = response.isError() ? "ERROR" : "SUCCESS";
            String summary = "args=" + sanitizeArguments(requestWithContext.getArguments())
                + ", result=" + abbreviate(response.getResult());
            try {
                agentMonitorService.recordToolCall(sessionId, runId, requestWithContext.getToolName(), status, summary, durationMs);
            } catch (RuntimeException ex) {
                log.warn("skip tool trace record: {}", ex.getMessage());
            }
        }
        return response;
    }

    private String sanitizeArguments(String arguments) {
        if (arguments == null) {
            return "";
        }
        String compact = arguments.replaceAll("\\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) + "..." : compact;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 120 ? compact.substring(0, 120) + "..." : compact;
    }

    private ToolCallRequest augmentContext(ToolCallRequest request) {
        String sessionId = SessionContextHolder.getSessionId();
        String runId = SessionContextHolder.getRunId();
        if (sessionId == null && runId == null) {
            return request;
        }
        Map<String, Object> context = new java.util.HashMap<>(request.getContext());
        if (sessionId != null) {
            context.put(ToolSessionSupport.SESSION_ID_KEY, sessionId);
        }
        if (runId != null) {
            context.put(ToolSessionSupport.RUN_ID_KEY, runId);
        }
        return ToolCallRequest.builder(request)
            .context(context)
            .build();
    }
}
