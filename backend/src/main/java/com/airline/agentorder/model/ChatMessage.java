package com.airline.agentorder.model;

import com.airline.agentorder.enums.MessageRole;

import java.time.LocalDateTime;

public class ChatMessage {

    private MessageRole role;
    private String content;
    private LocalDateTime time;

    public ChatMessage() {
    }

    public ChatMessage(MessageRole role, String content, LocalDateTime time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}
