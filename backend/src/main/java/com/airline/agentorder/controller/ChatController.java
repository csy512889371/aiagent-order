package com.airline.agentorder.controller;

import com.airline.agentorder.dto.ApiResponse;
import com.airline.agentorder.dto.AgentTraceResponse;
import com.airline.agentorder.dto.ChatMessageRequest;
import com.airline.agentorder.dto.ChatMessageResponse;
import com.airline.agentorder.dto.CreateSessionResponse;
import com.airline.agentorder.dto.HumanApprovalRequest;
import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.service.AgentMonitorService;
import com.airline.agentorder.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AgentMonitorService agentMonitorService;

    public ChatController(ChatService chatService, AgentMonitorService agentMonitorService) {
        this.chatService = chatService;
        this.agentMonitorService = agentMonitorService;
    }

    @PostMapping("/sessions")
    public ApiResponse<CreateSessionResponse> createSession() {
        ChatSession session = chatService.createSession();
        String welcomeMessage = session.getMessageList().get(0).getContent();
        return ApiResponse.success(new CreateSessionResponse(session.getSessionId(), welcomeMessage));
    }

    @PostMapping("/messages")
    public ApiResponse<ChatMessageResponse> chat(@Valid @RequestBody ChatMessageRequest request) {
        return ApiResponse.success(chatService.chat(request.getSessionId(), request.getMessage()));
    }

    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody ChatMessageRequest request) {
        return chatService.streamChat(request.getSessionId(), request.getMessage());
    }

    @PostMapping("/sessions/{sessionId}/approval")
    public ApiResponse<ChatMessageResponse> approve(
        @PathVariable String sessionId,
        @Valid @RequestBody HumanApprovalRequest request
    ) {
        return ApiResponse.success(chatService.approve(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/trace")
    public ApiResponse<AgentTraceResponse> trace(@PathVariable String sessionId) {
        return ApiResponse.success(agentMonitorService.getTrace(sessionId));
    }
}
