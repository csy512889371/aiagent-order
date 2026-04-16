package com.airline.agentorder.dto;

import jakarta.validation.constraints.NotBlank;

public class HumanApprovalRequest {

    @NotBlank
    private String action;

    private String comment;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
