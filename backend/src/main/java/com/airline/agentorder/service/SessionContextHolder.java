package com.airline.agentorder.service;

public final class SessionContextHolder {

    private static final ThreadLocal<String> SESSION_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> RUN_HOLDER = new ThreadLocal<>();

    private SessionContextHolder() {
    }

    public static void setContext(String sessionId, String runId) {
        SESSION_HOLDER.set(sessionId);
        RUN_HOLDER.set(runId);
    }

    public static String getSessionId() {
        return SESSION_HOLDER.get();
    }

    public static String getRunId() {
        return RUN_HOLDER.get();
    }

    public static void clear() {
        SESSION_HOLDER.remove();
        RUN_HOLDER.remove();
    }
}
