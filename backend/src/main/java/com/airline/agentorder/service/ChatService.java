package com.airline.agentorder.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.airline.agentorder.agent.AgentExecutionResult;
import com.airline.agentorder.agent.AirlineCustomerSupportAgent;
import com.airline.agentorder.dto.ApprovalRequestInfo;
import com.airline.agentorder.dto.ChatMessageResponse;
import com.airline.agentorder.dto.HumanApprovalRequest;
import com.airline.agentorder.enums.PendingActionType;
import com.airline.agentorder.enums.IntentType;
import com.airline.agentorder.enums.OrderStatus;
import com.airline.agentorder.enums.MessageRole;
import com.airline.agentorder.model.ChatSession;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Pattern ABORT_PATTERN = Pattern.compile(".*(先不退|暂不退|取消办理|不用了|算了).*");

    private final SessionService sessionService;
    private final OrderService orderService;
    private final AirlineCustomerSupportAgent supportAgent;
    private final AgentMonitorService agentMonitorService;

    public ChatService(
        SessionService sessionService,
        OrderService orderService,
        AirlineCustomerSupportAgent supportAgent,
        AgentMonitorService agentMonitorService
    ) {
        this.sessionService = sessionService;
        this.orderService = orderService;
        this.supportAgent = supportAgent;
        this.agentMonitorService = agentMonitorService;
    }

    public ChatSession createSession() {
        return sessionService.createSession();
    }

    public ChatMessageResponse chat(String sessionId, String message) {
        ChatSession session = sessionService.getRequiredSession(sessionId);
        if (ABORT_PATTERN.matcher(message).matches()) {
            sessionService.clearPendingState(session);
        }

        sessionService.appendMessage(session, MessageRole.USER, message);
        String runId = agentMonitorService.startRun(sessionId, message);
        AgentExecutionResult executionResult;
        try {
            executionResult = supportAgent.reply(sessionId, runId, message);
            agentMonitorService.completeRun(sessionId, runId, executionResult.getReply());
        } catch (RuntimeException ex) {
            agentMonitorService.failRun(sessionId, runId, ex.getMessage());
            throw ex;
        }

        if (executionResult.isInterrupted()) {
            sessionService.markPendingHumanApproval(session, executionResult.getInterruptionMetadata());
        } else {
            sessionService.clearPendingHumanApproval(session);
            ensureButtonConfirmationState(session, executionResult.getReply());
        }

        sessionService.appendMessage(session, MessageRole.ASSISTANT, executionResult.getReply());

        List<com.airline.agentorder.model.Order> updatedOrders =
            sessionService.consumeOrderListDirty(session) ? orderService.listOrders() : null;
        return new ChatMessageResponse(
            executionResult.getReply(),
            session.isPendingConfirmation(),
            session.isPendingHumanApproval(),
            buildApprovalRequest(session),
            updatedOrders
        );
    }

    public SseEmitter streamChat(String sessionId, String message) {
        ChatSession session = sessionService.getRequiredSession(sessionId);
        if (ABORT_PATTERN.matcher(message).matches()) {
            sessionService.clearPendingState(session);
        }

        sessionService.appendMessage(session, MessageRole.USER, message);
        String runId = agentMonitorService.startRun(sessionId, message);
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> doStreamChat(session, message, runId, emitter));
        return emitter;
    }

    public ChatMessageResponse approve(String sessionId, HumanApprovalRequest request) {
        ChatSession session = sessionService.getRequiredSession(sessionId);
        if (!session.isPendingHumanApproval()) {
            return handlePendingConfirmationAction(session, request);
        }

        InterruptionMetadata interruptionMetadata = sessionService.getRequiredPendingInterruption(sessionId);
        InterruptionMetadata feedback = buildFeedback(interruptionMetadata, request);
        String runId = agentMonitorService.startRun(sessionId, "[人工审批] " + request.getAction());

        AgentExecutionResult executionResult;
        try {
            executionResult = supportAgent.resumeWithHumanFeedback(sessionId, runId, feedback);
            agentMonitorService.completeRun(sessionId, runId, executionResult.getReply());
        } catch (RuntimeException ex) {
            agentMonitorService.failRun(sessionId, runId, ex.getMessage());
            throw ex;
        }

        if (executionResult.isInterrupted()) {
            sessionService.markPendingHumanApproval(session, executionResult.getInterruptionMetadata());
        } else {
            sessionService.clearPendingState(session);
        }

        sessionService.appendMessage(session, MessageRole.ASSISTANT, executionResult.getReply());
        List<com.airline.agentorder.model.Order> updatedOrders =
            sessionService.consumeOrderListDirty(session) ? orderService.listOrders() : null;

        return new ChatMessageResponse(
            executionResult.getReply(),
            session.isPendingConfirmation(),
            session.isPendingHumanApproval(),
            buildApprovalRequest(session),
            updatedOrders
        );
    }

    private InterruptionMetadata buildFeedback(InterruptionMetadata interruptionMetadata, HumanApprovalRequest request) {
        InterruptionMetadata.ToolFeedback.FeedbackResult result = parseApprovalAction(request.getAction());
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder(interruptionMetadata);
        builder.toolFeedbacks(List.of());
        for (InterruptionMetadata.ToolFeedback toolFeedback : interruptionMetadata.toolFeedbacks()) {
            builder.addToolFeedback(
                InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                    .result(result)
                    .description(request.getComment())
                    .build()
            );
        }
        return builder.build();
    }

    private InterruptionMetadata.ToolFeedback.FeedbackResult parseApprovalAction(String action) {
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "APPROVE" -> InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED;
            case "REJECTED", "REJECT" -> InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED;
            default -> throw new IllegalArgumentException("不支持的审批动作：" + action);
        };
    }

    private ApprovalRequestInfo buildApprovalRequest(ChatSession session) {
        if (session.isPendingHumanApproval() && session.getPendingInterruptionMetadata() != null) {
            return session.getPendingInterruptionMetadata().toolFeedbacks().stream().findFirst().map(item -> {
                ApprovalRequestInfo info = new ApprovalRequestInfo();
                info.setToolCallId(item.getId());
                info.setToolName(item.getName());
                info.setArguments(item.getArguments());
                info.setDescription(item.getDescription());
                return info;
            }).orElse(null);
        }

        if (!session.isPendingConfirmation()) {
            return null;
        }

        ApprovalRequestInfo info = new ApprovalRequestInfo();
        if (session.getPendingAction() == PendingActionType.WAIT_CONFIRM_CHANGE) {
            info.setToolName("change_order");
            info.setArguments(
                "bookingNo=" + session.getExtractedBookingNo()
                    + ", customerName=" + session.getExtractedCustomerName()
                    + ", newFlightDate=" + session.getRequestedChangeDate()
            );
            info.setDescription("请确认是否继续办理当前更改预订申请。点击“确认”后将执行改签；点击“取消”则结束本次办理。");
        } else {
            info.setToolName("cancel_order");
            info.setArguments("bookingNo=" + session.getExtractedBookingNo() + ", customerName=" + session.getExtractedCustomerName());
            info.setDescription("请确认是否继续办理当前退票申请。点击“确认”后将进入退票执行；点击“取消”则结束本次办理。");
        }
        return info;
    }

    private ChatMessageResponse handlePendingConfirmationAction(ChatSession session, HumanApprovalRequest request) {
        InterruptionMetadata.ToolFeedback.FeedbackResult action = parseApprovalAction(request.getAction());
        String sessionId = session.getSessionId();
        PendingActionType pendingAction = session.getPendingAction();

        sessionService.appendMessage(session, MessageRole.USER, action == InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED ? "确认" : "取消");

        if (action == InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED) {
            sessionService.clearPendingState(session);
            String reply = pendingAction == PendingActionType.WAIT_CONFIRM_CHANGE
                ? "已取消本次更改预订办理。如需重新申请改签，随时告诉我。"
                : "已取消本次退票办理。如需重新申请退票，随时告诉我。";
            sessionService.appendMessage(session, MessageRole.ASSISTANT, reply);
            return new ChatMessageResponse(reply, false, false, null, null);
        }

        String triggerMessage = pendingAction == PendingActionType.WAIT_CONFIRM_CHANGE
            ? "系统提示：用户已通过界面按钮确认当前更改预订申请。请基于当前会话继续办理，不要重复询问信息，不要重复解释规则。若条件满足，请直接调用 change_order。"
            : "系统提示：用户已通过界面按钮确认当前退票申请。请基于当前会话继续办理，不要重复询问信息，不要重复解释规则。若条件满足，请直接调用 cancel_order。";
        String triggerRunId = agentMonitorService.startRun(sessionId, "[按钮确认] APPROVED");
        AgentExecutionResult triggerResult;
        try {
            triggerResult = supportAgent.reply(sessionId, triggerRunId, triggerMessage);
            agentMonitorService.completeRun(sessionId, triggerRunId, triggerResult.getReply());
        } catch (RuntimeException ex) {
            agentMonitorService.failRun(sessionId, triggerRunId, ex.getMessage());
            throw ex;
        }

        AgentExecutionResult finalResult = triggerResult;
        if (pendingAction == PendingActionType.WAIT_CONFIRM_CANCEL && triggerResult.isInterrupted()) {
            String approvalRunId = agentMonitorService.startRun(sessionId, "[界面审批] APPROVED");
            try {
                finalResult = supportAgent.resumeWithHumanFeedback(
                    sessionId,
                    approvalRunId,
                    buildFeedback(triggerResult.getInterruptionMetadata(), request)
                );
                agentMonitorService.completeRun(sessionId, approvalRunId, finalResult.getReply());
            } catch (RuntimeException ex) {
                agentMonitorService.failRun(sessionId, approvalRunId, ex.getMessage());
                throw ex;
            }
        }

        if (finalResult.isInterrupted()) {
            sessionService.markPendingHumanApproval(session, finalResult.getInterruptionMetadata());
        } else {
            sessionService.clearPendingState(session);
        }

        sessionService.appendMessage(session, MessageRole.ASSISTANT, finalResult.getReply());
        List<com.airline.agentorder.model.Order> updatedOrders =
            sessionService.consumeOrderListDirty(session) ? orderService.listOrders() : null;
        return new ChatMessageResponse(
            finalResult.getReply(),
            session.isPendingConfirmation(),
            session.isPendingHumanApproval(),
            buildApprovalRequest(session),
            updatedOrders
        );
    }

    private void ensureButtonConfirmationState(ChatSession session, String reply) {
        if (session.isPendingConfirmation() || session.isPendingHumanApproval()) {
            return;
        }
        if (reply == null || reply.isBlank()) {
            return;
        }
        if (!mentionsApprovalButtons(reply)) {
            return;
        }
        if (session.getExtractedBookingNo() == null || session.getExtractedCustomerName() == null) {
            return;
        }

        orderService.queryOrder(session.getExtractedBookingNo(), session.getExtractedCustomerName())
            .ifPresent(order -> {
                if (session.getCurrentIntent() == IntentType.CHANGE_BOOKING) {
                    if (session.getRequestedChangeDate() != null && order.isCanChange() && order.getStatus() != OrderStatus.CANCELLED) {
                        sessionService.markAwaitingChangeConfirmation(session);
                    }
                    return;
                }
                if (order.isCanCancel() && order.getStatus() != OrderStatus.CANCELLED) {
                    sessionService.markAwaitingCancelConfirmation(session);
                }
            });
    }

    private boolean mentionsApprovalButtons(String reply) {
        return reply.contains("确认")
            && reply.contains("取消")
            && reply.contains("按钮");
    }

    private void doStreamChat(ChatSession session, String message, String runId, SseEmitter emitter) {
        AtomicReference<NodeOutput> lastOutputRef = new AtomicReference<>();
        AtomicReference<String> lastToolCallSignature = new AtomicReference<>();
        AtomicReference<String> lastToolResultSignature = new AtomicReference<>();
        StringBuilder streamedReply = new StringBuilder();
        Flux<NodeOutput> stream = supportAgent.streamReply(session.getSessionId(), runId, message);

        stream.doOnNext(nodeOutput -> {
                lastOutputRef.set(nodeOutput);
                String delta = supportAgent.extractStreamingText(nodeOutput);
                if (delta != null && !delta.isEmpty()) {
                    streamedReply.append(delta);
                    sendEvent(emitter, "chunk", Map.of("content", delta));
                }
                AirlineCustomerSupportAgent.ToolStreamEvent toolEvent = supportAgent.extractToolStreamEvent(nodeOutput);
                if (toolEvent == null) {
                    return;
                }

                if ("toolCall".equals(toolEvent.type())) {
                    if (toolEvent.signature().equals(lastToolCallSignature.get())) {
                        return;
                    }
                    lastToolCallSignature.set(toolEvent.signature());
                    sendEvent(emitter, "toolCall", Map.of("content", toolEvent.content()));
                    return;
                }

                if ("toolResult".equals(toolEvent.type())) {
                    if (toolEvent.signature().equals(lastToolResultSignature.get())) {
                        return;
                    }
                    lastToolResultSignature.set(toolEvent.signature());
                    sendEvent(emitter, "toolResult", Map.of("content", toolEvent.content()));
                }
            })
            .doOnError(error -> {
                agentMonitorService.failRun(session.getSessionId(), runId, error.getMessage());
                sendEvent(emitter, "error", Map.of("message", "AI 智能体处理失败，请稍后重试。"));
                emitter.completeWithError(error);
            })
            .doOnComplete(() -> {
                AgentExecutionResult executionResult = supportAgent.mapNodeOutput(lastOutputRef.get());
                if ((executionResult.getReply() == null || executionResult.getReply().isBlank()) && streamedReply.length() > 0) {
                    executionResult = new AgentExecutionResult(streamedReply.toString(), executionResult.getInterruptionMetadata());
                }
                agentMonitorService.completeRun(session.getSessionId(), runId, executionResult.getReply());

                if (executionResult.isInterrupted()) {
                    sessionService.markPendingHumanApproval(session, executionResult.getInterruptionMetadata());
                } else {
                    sessionService.clearPendingHumanApproval(session);
                    ensureButtonConfirmationState(session, executionResult.getReply());
                }

                sessionService.appendMessage(session, MessageRole.ASSISTANT, executionResult.getReply());
                List<com.airline.agentorder.model.Order> updatedOrders =
                    sessionService.consumeOrderListDirty(session) ? orderService.listOrders() : null;
                sendEvent(emitter, "done", buildDonePayload(session, executionResult, updatedOrders));
                emitter.complete();
            })
            .subscribe();
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("流式消息发送失败", ex);
        }
    }

    private LinkedHashMap<String, Object> buildDonePayload(
        ChatSession session,
        AgentExecutionResult executionResult,
        List<com.airline.agentorder.model.Order> updatedOrders
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("reply", executionResult.getReply());
        payload.put("pendingConfirmation", session.isPendingConfirmation());
        payload.put("pendingHumanApproval", session.isPendingHumanApproval());
        payload.put("approvalRequest", buildApprovalRequest(session));
        payload.put("updatedOrders", updatedOrders);
        return payload;
    }
}
