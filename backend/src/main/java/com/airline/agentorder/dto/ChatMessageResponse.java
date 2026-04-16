package com.airline.agentorder.dto;

import com.airline.agentorder.model.Order;

import java.util.List;

public class ChatMessageResponse {

    private String reply;
    private boolean pendingConfirmation;
    private boolean pendingHumanApproval;
    private ApprovalRequestInfo approvalRequest;
    private List<Order> updatedOrders;

    public ChatMessageResponse() {
    }

    public ChatMessageResponse(
        String reply,
        boolean pendingConfirmation,
        boolean pendingHumanApproval,
        ApprovalRequestInfo approvalRequest,
        List<Order> updatedOrders
    ) {
        this.reply = reply;
        this.pendingConfirmation = pendingConfirmation;
        this.pendingHumanApproval = pendingHumanApproval;
        this.approvalRequest = approvalRequest;
        this.updatedOrders = updatedOrders;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isPendingConfirmation() {
        return pendingConfirmation;
    }

    public void setPendingConfirmation(boolean pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
    }

    public boolean isPendingHumanApproval() {
        return pendingHumanApproval;
    }

    public void setPendingHumanApproval(boolean pendingHumanApproval) {
        this.pendingHumanApproval = pendingHumanApproval;
    }

    public ApprovalRequestInfo getApprovalRequest() {
        return approvalRequest;
    }

    public void setApprovalRequest(ApprovalRequestInfo approvalRequest) {
        this.approvalRequest = approvalRequest;
    }

    public List<Order> getUpdatedOrders() {
        return updatedOrders;
    }

    public void setUpdatedOrders(List<Order> updatedOrders) {
        this.updatedOrders = updatedOrders;
    }
}
