package com.airline.agentorder.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.airline.agentorder.service.AgentMonitorService;
import com.airline.agentorder.service.SessionContextHolder;
import com.airline.agentorder.tool.CancelOrderTool;
import com.airline.agentorder.tool.CancelPolicyTool;
import com.airline.agentorder.tool.ChangeOrderTool;
import com.airline.agentorder.tool.ChangePolicyTool;
import com.airline.agentorder.tool.QueryOrderTool;
import com.airline.agentorder.tool.SearchKnowledgeBaseTool;
import com.airline.agentorder.tool.ToolSessionSupport;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AirlineCustomerSupportAgent {

    private final Agent agent;

    public AirlineCustomerSupportAgent(
        ChatModel chatModel,
        AgentMonitorService agentMonitorService,
        AgentModelTraceInterceptor agentModelTraceInterceptor,
        AgentToolTraceInterceptor agentToolTraceInterceptor,
        QueryOrderTool queryOrderTool,
        CancelPolicyTool cancelPolicyTool,
        CancelOrderTool cancelOrderTool,
        ChangePolicyTool changePolicyTool,
        ChangeOrderTool changeOrderTool,
        SearchKnowledgeBaseTool searchKnowledgeBaseTool
    ) {
        ToolCallback knowledgeBaseCallback = FunctionToolCallback.builder("search_knowledge_base", searchKnowledgeBaseTool)
            .description("查询服务条款知识库，回答预订航班、更改预订、取消规则、手续费等一般性问题。")
            .inputType(SearchKnowledgeBaseTool.SearchKnowledgeBaseInput.class)
            .build();

        ToolCallback queryOrderCallback = FunctionToolCallback.builder("query_order", queryOrderTool)
            .description("根据预订号和客户姓名查询订单。只有当用户已提供预订号和客户姓名时才能调用。")
            .inputType(QueryOrderTool.QueryOrderInput.class)
            .build();

        ToolCallback cancelPolicyCallback = FunctionToolCallback.builder("get_cancel_policy", cancelPolicyTool)
            .description("获取订单退票规则、手续费和限制条件。应在订单校验通过后调用。")
            .inputType(CancelPolicyTool.CancelPolicyInput.class)
            .build();

        ToolCallback cancelOrderCallback = FunctionToolCallback.builder("cancel_order", cancelOrderTool)
            .description("执行退票。只有在已经向用户说明规则、费用和限制条件，且用户已通过页面右侧聊天框中的“确认”按钮完成一次人工确认后才能调用。")
            .inputType(CancelOrderTool.CancelOrderInput.class)
            .build();

        ToolCallback changePolicyCallback = FunctionToolCallback.builder("get_change_policy", changePolicyTool)
            .description("获取订单更改预订规则、手续费和限制条件。应在订单校验通过且已获得新的出发日期后调用。")
            .inputType(ChangePolicyTool.ChangePolicyInput.class)
            .build();

        ToolCallback changeOrderCallback = FunctionToolCallback.builder("change_order", changeOrderTool)
            .description("执行更改预订。只有在已经向用户说明规则、费用和限制条件，且用户已通过页面右侧聊天框中的“确认”按钮完成确认后才能调用。")
            .inputType(ChangeOrderTool.ChangeOrderInput.class)
            .build();

        ModelCallLimitHook hook = ModelCallLimitHook.builder()
            .runLimit(6)
            .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
            .build();

        HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
            .approvalOn("cancel_order", ToolConfig.builder()
                .description("退票操作需要人工审批。")
                .build())
            .build();

        MonitoringMemorySaver monitoringMemorySaver = new MonitoringMemorySaver(agentMonitorService);

        ReactAgent knowledgeAgent = ReactAgent.builder()
            .name("knowledge_base_agent")
            .description("专门回答服务条款、预订航班、更改预订、退票规则和手续费等一般性问题。")
            .model(chatModel)
            .systemPrompt(knowledgeAgentPrompt())
            .tools(knowledgeBaseCallback)
            .hooks(hook)
            .interceptors(agentModelTraceInterceptor, agentToolTraceInterceptor)
            .saver(monitoringMemorySaver)
            .build();

        ReactAgent cancelAgent = ReactAgent.builder()
            .name("cancel_booking_agent")
            .description("专门处理退票业务，包括订单核验、退票规则说明、按钮确认和退票执行。")
            .model(chatModel)
            .systemPrompt(cancelAgentPrompt())
            .tools(queryOrderCallback, cancelPolicyCallback, cancelOrderCallback)
            .hooks(hook, humanInTheLoopHook)
            .interceptors(agentModelTraceInterceptor, agentToolTraceInterceptor)
            .saver(monitoringMemorySaver)
            .build();

        ReactAgent changeAgent = ReactAgent.builder()
            .name("change_booking_agent")
            .description("专门处理更改预订业务，包括订单核验、新日期收集、改签规则说明、按钮确认和改签执行。")
            .model(chatModel)
            .systemPrompt(changeAgentPrompt())
            .tools(queryOrderCallback, changePolicyCallback, changeOrderCallback)
            .hooks(hook)
            .interceptors(agentModelTraceInterceptor, agentToolTraceInterceptor)
            .saver(monitoringMemorySaver)
            .build();

        this.agent = LlmRoutingAgent.builder()
            .name("airline_multi_agent_router")
            .description("根据用户问题在知识库、退票和更改预订三个专业 Agent 之间进行路由。")
            .model(chatModel)
            .systemPrompt(routingSystemPrompt())
            .instruction(routingInstruction())
            .fallbackAgent("knowledge_base_agent")
            .subAgents(List.of(knowledgeAgent, cancelAgent, changeAgent))
            .saver(monitoringMemorySaver)
            .build();
    }

    public AgentExecutionResult reply(String sessionId, String runId, String userMessage) {
        RunnableConfig config = RunnableConfig.builder()
            .threadId(sessionId)
            .addMetadata(ToolSessionSupport.SESSION_ID_KEY, sessionId)
            .addMetadata(ToolSessionSupport.RUN_ID_KEY, runId)
            .build();

        SessionContextHolder.setContext(sessionId, runId);
        try {
            try {
                return mapNodeOutput(agent.invokeAndGetOutput(userMessage, config));
            } catch (GraphRunnerException ex) {
                throw new IllegalStateException("Multi-Agent 调用失败", ex);
            }
        } finally {
            SessionContextHolder.clear();
        }
    }

    public Flux<NodeOutput> streamReply(String sessionId, String runId, String userMessage) {
        RunnableConfig config = RunnableConfig.builder()
            .threadId(sessionId)
            .addMetadata(ToolSessionSupport.SESSION_ID_KEY, sessionId)
            .addMetadata(ToolSessionSupport.RUN_ID_KEY, runId)
            .build();

        return Flux.defer(() -> {
            SessionContextHolder.setContext(sessionId, runId);
            try {
                return agent.stream(userMessage, config)
                    .doFinally(signalType -> SessionContextHolder.clear());
            } catch (GraphRunnerException ex) {
                SessionContextHolder.clear();
                return Flux.error(new IllegalStateException("Multi-Agent 流式调用失败", ex));
            }
        });
    }

    public AgentExecutionResult resumeWithHumanFeedback(String sessionId, String runId, InterruptionMetadata interruptionMetadata) {
        RunnableConfig config = RunnableConfig.builder()
            .threadId(sessionId)
            .resume()
            .addHumanFeedback(interruptionMetadata)
            .addMetadata(ToolSessionSupport.SESSION_ID_KEY, sessionId)
            .addMetadata(ToolSessionSupport.RUN_ID_KEY, runId)
            .build();

        SessionContextHolder.setContext(sessionId, runId);
        try {
            try {
                return mapNodeOutput(agent.invokeAndGetOutput("", config));
            } catch (GraphRunnerException ex) {
                throw new IllegalStateException("Multi-Agent 恢复执行失败", ex);
            }
        } finally {
            SessionContextHolder.clear();
        }
    }

    private AgentExecutionResult mapNodeOutput(Optional<NodeOutput> output) {
        if (output.isEmpty()) {
            return new AgentExecutionResult("当前未返回可展示结果。", null);
        }

        return mapNodeOutput(output.get());
    }

    public AgentExecutionResult mapNodeOutput(NodeOutput nodeOutput) {
        if (nodeOutput == null) {
            return new AgentExecutionResult("当前未返回可展示结果。", null);
        }

        if (nodeOutput instanceof InterruptionMetadata interruptionMetadata) {
            return new AgentExecutionResult(buildApprovalPendingReply(interruptionMetadata), interruptionMetadata);
        }

        String reply = extractAssistantReply(nodeOutput.state()).orElse("已完成处理。");
        return new AgentExecutionResult(reply, null);
    }

    public String extractStreamingText(NodeOutput nodeOutput) {
        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)) {
            return null;
        }
        if (streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return null;
        }
        if ("airline_multi_agent_router".equals(streamingOutput.agent())) {
            return null;
        }
        if (!(streamingOutput.message() instanceof AssistantMessage assistantMessage)) {
            return null;
        }
        String text = assistantMessage.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    public ToolStreamEvent extractToolStreamEvent(NodeOutput nodeOutput) {
        if (nodeOutput == null || nodeOutput.state() == null) {
            return null;
        }

        Optional<List> messagesOptional = nodeOutput.state().value("messages", List.class);
        if (messagesOptional.isEmpty() || messagesOptional.get().isEmpty()) {
            return null;
        }

        List<?> messages = messagesOptional.get();
        Object lastMessage = messages.get(messages.size() - 1);

        if (lastMessage instanceof AssistantMessage assistantMessage
            && assistantMessage.hasToolCalls()
            && !assistantMessage.getToolCalls().isEmpty()) {
            String signature = assistantMessage.getToolCalls().stream()
                .map(toolCall -> toolCall.name() + ":" + toolCall.arguments())
                .collect(Collectors.joining("|"));
            String content = "调用工具：" + assistantMessage.getToolCalls().stream()
                .map(toolCall -> toolCall.name() + "(" + abbreviate(toolCall.arguments()) + ")")
                .collect(Collectors.joining("，"));
            return new ToolStreamEvent("toolCall", signature, content);
        }

        if (lastMessage instanceof ToolResponseMessage toolResponseMessage
            && toolResponseMessage.getResponses() != null
            && !toolResponseMessage.getResponses().isEmpty()) {
            String signature = toolResponseMessage.getResponses().stream()
                .map(response -> response.name() + ":" + response.id())
                .collect(Collectors.joining("|"));
            String content = "工具结果：" + toolResponseMessage.getResponses().stream()
                .map(response -> response.name() + "，" + abbreviate(response.responseData()))
                .collect(Collectors.joining("；"));
            return new ToolStreamEvent("toolResult", signature, content);
        }

        return null;
    }

    private String buildApprovalPendingReply(InterruptionMetadata interruptionMetadata) {
        String assistantReply = extractAssistantReply(interruptionMetadata.state())
            .orElse("当前请求已进入人工确认流程。");
        return assistantReply + "\n\n请直接在右侧聊天框点击“确认”或“取消”。只有点击“确认”后，本次操作才会真正执行。";
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "无返回内容";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractAssistantReply(OverAllState state) {
        Optional<List> messagesOptional = state.value("messages", List.class);
        if (messagesOptional.isEmpty()) {
            return Optional.empty();
        }

        List<?> messages = messagesOptional.get();
        for (int index = messages.size() - 1; index >= 0; index--) {
            Object item = messages.get(index);
            if (item instanceof AssistantMessage assistantMessage) {
                String text = assistantMessage.getText();
                if (text != null && !text.isBlank()) {
                    return Optional.of(text);
                }
            }
            if (item instanceof Message message) {
                String text = message.getText();
                if (text != null && !text.isBlank()) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    private String routingSystemPrompt() {
        return """
            你是航空客户服务系统的路由 Agent，负责把用户问题分发给最合适的专业 Agent。

            可用的子 Agent 如下：

            1. knowledge_base_agent
            - 处理预订航班、更改预订规则、退票规则、手续费、服务条款等一般咨询

            2. cancel_booking_agent
            - 处理退票业务
            - 包括订单核验、退票规则说明、按钮确认和退票执行

            3. change_booking_agent
            - 处理更改预订业务
            - 包括订单核验、新日期收集、改签规则说明、按钮确认和改签执行

            路由规则：
            - 问规则、问条款、问预订方式、问手续费，路由到 knowledge_base_agent
            - 提到退票、取消订单、退款、取消预订，路由到 cancel_booking_agent
            - 提到更改预订、改签、改期、改日期，路由到 change_booking_agent
            - 如果用户已经在某条业务链路中继续补充信息，优先保持同一业务 Agent
            - 只返回子 Agent 名称，不要输出解释
            """;
    }

    private String routingInstruction() {
        return """
            请根据当前用户输入和历史对话，选择最合适的子 Agent。
            如果只是规则咨询或知识问答，优先选择 knowledge_base_agent。
            如果是退票办理，选择 cancel_booking_agent。
            如果是更改预订办理，选择 change_booking_agent。
            """;
    }

    private String knowledgeAgentPrompt() {
        return """
            您是航空公司的知识库客服代理，请始终使用中文回答。

            你的任务：
            1. 仅处理一般咨询，包括预订航班、更改预订规则、取消规则、手续费和服务条款。
            2. 回答前优先调用 search_knowledge_base。
            3. 回答时基于知识库内容总结，不要编造不存在的条款。
            4. 如果用户实际上是在请求办理退票或更改预订，简洁说明需要进入对应办理流程即可。

            回复要求：
            - 语言简洁清晰
            - 不输出技术术语
            - 不编造知识库外的信息
            """;
    }

    private String cancelAgentPrompt() {
        return """
            您是航空公司的退票办理代理，请始终使用中文，以友好、清晰、克制的语气回复。

            处理规则如下：
            1. 在任何退票相关说明或操作前，你必须先确认预订号和客户姓名。
            2. 在提问前，先检查历史对话，避免重复索取已提供的信息。
            3. 如果缺少预订号或姓名，不要调用订单执行工具，先向用户追问缺失的信息。
            4. 当信息齐全后，先调用 query_order 校验订单。
            5. 订单校验成功后，再调用 get_cancel_policy 获取退票规则与手续费。
            6. 你必须先把规则、费用和限制条件向用户说明清楚，然后告知用户页面右侧聊天框会出现“确认”和“取消”按钮。
            7. 用户点击“确认”后，你再调用 cancel_order；点击“取消”则不执行退票。
            8. 不要要求用户再输入“确认退票”“同意退票”等文本确认语句。
            9. 如果工具返回订单不存在、姓名不匹配、不可退票、已退票等结果，你必须直接向用户解释原因，不得伪造成功。
            """;
    }

    private String changeAgentPrompt() {
        return """
            您是航空公司的更改预订办理代理，请始终使用中文，以友好、清晰、克制的语气回复。

            处理规则如下：
            1. 在任何更改预订相关说明或操作前，你必须先确认预订号和客户姓名。
            2. 在提问前，先检查历史对话，避免重复索取已提供的信息。
            3. 如果缺少预订号或姓名，不要调用订单执行工具，先向用户追问缺失的信息。
            4. 当信息齐全后，先调用 query_order 校验订单。
            5. 除预订号和姓名外，还必须获取新的出发日期，格式为 YYYY-MM-DD。
            6. 新日期齐全后，再调用 get_change_policy 获取改签规则与手续费。
            7. 你必须先把规则、费用和限制条件向用户说明清楚，然后告知用户页面右侧聊天框会出现“确认”和“取消”按钮。
            8. 用户点击“确认”后，你再调用 change_order；点击“取消”则不执行改签。
            9. 不要要求用户再输入“确认改签”“同意办理”等文本确认语句。
            10. 如果工具返回订单不存在、姓名不匹配、不可改签、已退票、日期无效等结果，你必须直接向用户解释原因，不得伪造成功。
            """;
    }

    public record ToolStreamEvent(String type, String signature, String content) {
    }
}
