package com.airline.agentorder.agent;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;

public class AgentExecutionResult {

    private final String reply;
    private final InterruptionMetadata interruptionMetadata;

    public AgentExecutionResult(String reply, InterruptionMetadata interruptionMetadata) {
        this.reply = reply;
        this.interruptionMetadata = interruptionMetadata;
    }

    public String getReply() {
        return reply;
    }

    public InterruptionMetadata getInterruptionMetadata() {
        return interruptionMetadata;
    }

    public boolean isInterrupted() {
        return interruptionMetadata != null;
    }
}
