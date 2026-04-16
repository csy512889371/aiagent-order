package com.airline.agentorder.tool;

import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.service.SessionService;
import org.springframework.ai.chat.model.ToolContext;

public final class ToolSessionSupport {

    public static final String SESSION_ID_KEY = "sessionId";
    public static final String RUN_ID_KEY = "runId";

    private ToolSessionSupport() {
    }

    public static ChatSession getRequiredSession(SessionService sessionService, ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new IllegalStateException("工具上下文缺少会话信息。");
        }
        Object sessionId = toolContext.getContext().get(SESSION_ID_KEY);
        if (!(sessionId instanceof String sessionIdText) || sessionIdText.isBlank()) {
            throw new IllegalStateException("工具上下文缺少 sessionId。");
        }
        return sessionService.getRequiredSession(sessionIdText);
    }
}
