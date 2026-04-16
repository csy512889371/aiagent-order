package com.airline.agentorder.service;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.airline.agentorder.enums.IntentType;
import com.airline.agentorder.enums.MessageRole;
import com.airline.agentorder.enums.PendingActionType;
import com.airline.agentorder.model.ChatMessage;
import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.repository.InMemorySessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SessionService {

    private final InMemorySessionRepository sessionRepository;
    private final String welcomeMessage;

    public SessionService(
        InMemorySessionRepository sessionRepository,
        @Value("${app.chat.welcome-message}") String welcomeMessage
    ) {
        this.sessionRepository = sessionRepository;
        this.welcomeMessage = welcomeMessage;
    }

    public ChatSession createSession() {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setCreatedTime(LocalDateTime.now());
        session.setUpdatedTime(LocalDateTime.now());
        session.setCurrentIntent(IntentType.UNKNOWN);
        session.setPendingAction(PendingActionType.NONE);
        appendMessage(session, MessageRole.ASSISTANT, welcomeMessage);
        sessionRepository.save(session);
        return session;
    }

    public ChatSession getRequiredSession(String sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("session not found"));
    }

    public void appendMessage(ChatSession session, MessageRole role, String content) {
        session.getMessageList().add(new ChatMessage(role, content, LocalDateTime.now()));
        session.setUpdatedTime(LocalDateTime.now());
        sessionRepository.save(session);
    }

    public void rememberBookingInfo(ChatSession session, String bookingNo, String customerName) {
        session.setExtractedBookingNo(bookingNo);
        session.setExtractedCustomerName(customerName);
        sessionRepository.save(session);
    }

    public void rememberChangeDate(ChatSession session, String requestedChangeDate) {
        session.setRequestedChangeDate(requestedChangeDate);
        sessionRepository.save(session);
    }

    public void markAwaitingCancelBookingInfo(ChatSession session) {
        session.setCurrentIntent(IntentType.CANCEL_BOOKING);
        session.setPendingAction(PendingActionType.WAIT_BOOKING_INFO);
        session.setPendingConfirmation(false);
        sessionRepository.save(session);
    }

    public void markAwaitingChangeBookingInfo(ChatSession session) {
        session.setCurrentIntent(IntentType.CHANGE_BOOKING);
        session.setPendingAction(PendingActionType.WAIT_BOOKING_INFO);
        session.setPendingConfirmation(false);
        sessionRepository.save(session);
    }

    public void markAwaitingCancelConfirmation(ChatSession session) {
        session.setCurrentIntent(IntentType.CANCEL_BOOKING);
        session.setPendingAction(PendingActionType.WAIT_CONFIRM_CANCEL);
        session.setPendingConfirmation(true);
        sessionRepository.save(session);
    }

    public void markAwaitingChangeConfirmation(ChatSession session) {
        session.setCurrentIntent(IntentType.CHANGE_BOOKING);
        session.setPendingAction(PendingActionType.WAIT_CONFIRM_CHANGE);
        session.setPendingConfirmation(true);
        sessionRepository.save(session);
    }

    public void clearPendingState(ChatSession session) {
        session.setPendingAction(PendingActionType.NONE);
        session.setPendingConfirmation(false);
        session.setUserConfirmedThisTurn(false);
        session.setPendingHumanApproval(false);
        session.setPendingInterruptionMetadata(null);
        session.setRequestedChangeDate(null);
        sessionRepository.save(session);
    }

    public void markOrderListDirty(ChatSession session) {
        session.setOrderListDirty(true);
        sessionRepository.save(session);
    }

    public boolean consumeOrderListDirty(ChatSession session) {
        boolean dirty = session.isOrderListDirty();
        session.setOrderListDirty(false);
        sessionRepository.save(session);
        return dirty;
    }

    public void markPendingHumanApproval(ChatSession session, InterruptionMetadata interruptionMetadata) {
        session.setPendingHumanApproval(true);
        session.setPendingInterruptionMetadata(interruptionMetadata);
        sessionRepository.save(session);
    }

    public void clearPendingHumanApproval(ChatSession session) {
        session.setPendingHumanApproval(false);
        session.setPendingInterruptionMetadata(null);
        sessionRepository.save(session);
    }

    public InterruptionMetadata getRequiredPendingInterruption(String sessionId) {
        ChatSession session = getRequiredSession(sessionId);
        if (!session.isPendingHumanApproval() || session.getPendingInterruptionMetadata() == null) {
            throw new NoSuchElementException("pending human approval not found");
        }
        return session.getPendingInterruptionMetadata();
    }
}
