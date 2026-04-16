package com.airline.agentorder.model;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.airline.agentorder.enums.IntentType;
import com.airline.agentorder.enums.PendingActionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChatSession {

    private String sessionId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private IntentType currentIntent;
    private PendingActionType pendingAction;
    private boolean pendingConfirmation;
    private String extractedBookingNo;
    private String extractedCustomerName;
    private String requestedChangeDate;
    private boolean userConfirmedThisTurn;
    private boolean orderListDirty;
    private boolean pendingHumanApproval;
    private InterruptionMetadata pendingInterruptionMetadata;
    private final List<ChatMessage> messageList = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public IntentType getCurrentIntent() {
        return currentIntent;
    }

    public void setCurrentIntent(IntentType currentIntent) {
        this.currentIntent = currentIntent;
    }

    public PendingActionType getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(PendingActionType pendingAction) {
        this.pendingAction = pendingAction;
    }

    public boolean isPendingConfirmation() {
        return pendingConfirmation;
    }

    public void setPendingConfirmation(boolean pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
    }

    public String getExtractedBookingNo() {
        return extractedBookingNo;
    }

    public void setExtractedBookingNo(String extractedBookingNo) {
        this.extractedBookingNo = extractedBookingNo;
    }

    public String getExtractedCustomerName() {
        return extractedCustomerName;
    }

    public void setExtractedCustomerName(String extractedCustomerName) {
        this.extractedCustomerName = extractedCustomerName;
    }

    public String getRequestedChangeDate() {
        return requestedChangeDate;
    }

    public void setRequestedChangeDate(String requestedChangeDate) {
        this.requestedChangeDate = requestedChangeDate;
    }

    public boolean isUserConfirmedThisTurn() {
        return userConfirmedThisTurn;
    }

    public void setUserConfirmedThisTurn(boolean userConfirmedThisTurn) {
        this.userConfirmedThisTurn = userConfirmedThisTurn;
    }

    public boolean isOrderListDirty() {
        return orderListDirty;
    }

    public void setOrderListDirty(boolean orderListDirty) {
        this.orderListDirty = orderListDirty;
    }

    public boolean isPendingHumanApproval() {
        return pendingHumanApproval;
    }

    public void setPendingHumanApproval(boolean pendingHumanApproval) {
        this.pendingHumanApproval = pendingHumanApproval;
    }

    public InterruptionMetadata getPendingInterruptionMetadata() {
        return pendingInterruptionMetadata;
    }

    public void setPendingInterruptionMetadata(InterruptionMetadata pendingInterruptionMetadata) {
        this.pendingInterruptionMetadata = pendingInterruptionMetadata;
    }

    public List<ChatMessage> getMessageList() {
        return messageList;
    }
}
