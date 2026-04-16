<template>
  <main class="shell">
    <section class="hero">
      <div>
        <p class="eyebrow">Spring AI Alibaba + ReactAgent</p>
        <h1>航空订单 AI 退票演示</h1>
        <p class="hero-copy">
          当前版本支持知识库问答、退票和更改预订。用户通过右侧 AI 智能体完成规则咨询、订单核验和按钮确认。
        </p>
      </div>
    </section>

    <section class="layout">
      <OrderTable :orders="orders" @cancel="handleQuickCancel" @change="handleQuickChange" />
      <div class="right-stack">
        <ChatPanel
          :messages="messages"
          :pending-approval="pendingApproval"
          :loading="sending"
          @send="handleSendMessage"
          @approval="handleApproval"
        />
        <AgentTracePanel :trace="trace" />
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from "vue";
import OrderTable from "./components/OrderTable.vue";
import ChatPanel from "./components/ChatPanel.vue";
import AgentTracePanel from "./components/AgentTracePanel.vue";
import { createSession, streamMessage, submitApproval } from "./api/chat";
import { fetchOrders } from "./api/order";
import { fetchTrace } from "./api/trace";

const sessionId = ref("");
const sending = ref(false);
const orders = ref([]);
const messages = ref([]);
const trace = ref(null);
const pendingApproval = ref(null);
const bootstrapped = ref(false);

onMounted(async () => {
  try {
    await Promise.all([initSession(), loadOrders()]);
    bootstrapped.value = true;
  } catch (error) {
    messages.value = [
      {
        role: "assistant",
        content: `初始化失败：${error.message || "请检查后端服务和接口配置。"}`,
        time: formatTime()
      }
    ];
  }
});

async function initSession() {
  return initSessionState(true);
}

async function initSessionState(resetMessages) {
  const response = await createSession();
  const data = response.data;
  sessionId.value = data.sessionId;
  if (resetMessages) {
    messages.value = [
      {
        role: "assistant",
        content: data.welcomeMessage,
        time: formatTime()
      }
    ];
  }
  return data;
}

async function loadOrders() {
  const response = await fetchOrders();
  orders.value = response.data ?? [];
}

async function handleQuickCancel(order) {
  const text = `我要退票，预订号是 ${order.bookingNo}，姓名是 ${order.customerName}`;
  await handleSendMessage(text);
}

async function handleQuickChange(order) {
  const text = `我要更改预订，预订号是 ${order.bookingNo}，姓名是 ${order.customerName}`;
  await handleSendMessage(text);
}

async function handleSendMessage(content) {
  if (!sessionId.value) {
    if (!bootstrapped.value) {
      messages.value.push({
        role: "assistant",
        content: "当前会话尚未初始化完成，请先检查后端服务。",
        time: formatTime()
      });
    }
    return;
  }

  messages.value.push({
    role: "user",
    content,
    time: formatTime()
  });
  const assistantMessage = {
    role: "assistant",
    content: "",
    time: formatTime(),
    kind: "assistant"
  };
  messages.value.push(assistantMessage);

  sending.value = true;
  try {
    await sendMessageWithRecovery(content, {
      onChunk(payload) {
        assistantMessage.content += payload.content || "";
      },
      onToolCall(payload) {
        messages.value.push({
          role: "assistant",
          kind: "tool",
          content: payload.content || "正在调用工具",
          time: formatTime()
        });
      },
      onToolResult(payload) {
        messages.value.push({
          role: "assistant",
          kind: "tool",
          content: payload.content || "工具调用完成",
          time: formatTime()
        });
      },
      onDone(payload) {
        assistantMessage.content = payload.reply || assistantMessage.content || "已完成处理。";
        pendingApproval.value = payload.pendingHumanApproval || payload.pendingConfirmation ? payload.approvalRequest : null;
        if (Array.isArray(payload.updatedOrders)) {
          orders.value = payload.updatedOrders;
        }
      },
      onError(payload) {
        assistantMessage.content = payload.message || "当前请求失败，请检查后端服务是否已经启动。";
      }
    });
    await loadTrace();
  } catch (error) {
    assistantMessage.content = error.message || "当前请求失败，请检查后端服务是否已经启动。";
  } finally {
    sending.value = false;
  }
}

async function loadTrace() {
  if (!sessionId.value) {
    return;
  }
  try {
    const response = await fetchTrace(sessionId.value);
    trace.value = response.data;
  } catch (error) {
    trace.value = null;
  }
}

async function handleApproval(action) {
  if (!sessionId.value || !pendingApproval.value) {
    return;
  }

  messages.value.push({
    role: "user",
    content: action === "APPROVED" ? "确认" : "取消",
    time: formatTime()
  });

  sending.value = true;
  try {
    const response = await submitApprovalWithRecovery(action, {
      action,
      comment: action === "REJECTED" ? "用户点击取消，本次操作不执行。" : "用户点击确认，允许执行当前操作。"
    });

    const data = response.data;
    messages.value.push({
      role: "assistant",
      content: data.reply,
      time: formatTime()
    });
    pendingApproval.value = data.pendingHumanApproval || data.pendingConfirmation ? data.approvalRequest : null;

    if (Array.isArray(data.updatedOrders)) {
      orders.value = data.updatedOrders;
    }
    await loadTrace();
  } catch (error) {
    messages.value.push({
      role: "assistant",
      content: error.message || "人工审批提交失败。",
      time: formatTime()
    });
  } finally {
    sending.value = false;
  }
}

async function sendMessageWithRecovery(content, handlers) {
  try {
    return await streamMessage({
      sessionId: sessionId.value,
      message: content
    }, handlers);
  } catch (error) {
    if (!isExpiredSessionError(error)) {
      throw error;
    }
    await recoverSession(false);
    return streamMessage({
      sessionId: sessionId.value,
      message: content
    }, handlers);
  }
}

async function submitApprovalWithRecovery(action, payload) {
  try {
    return await submitApproval(sessionId.value, payload);
  } catch (error) {
    if (!isExpiredSessionError(error)) {
      throw error;
    }
    await recoverSession(false);
    pendingApproval.value = null;
    return {
      data: {
        reply: action === "REJECTED"
          ? "原会话已失效，系统已自动重建会话，本次取消无需继续处理。"
          : "原会话已失效，系统已自动重建会话。请重新发起一次退票或更改预订请求。",
        pendingHumanApproval: false,
        pendingConfirmation: false,
        approvalRequest: null,
        updatedOrders: null
      }
    };
  }
}

async function recoverSession(resetMessages = false) {
  await initSessionState(resetMessages);
  pendingApproval.value = null;
  trace.value = null;
}

function isExpiredSessionError(error) {
  return error?.code === 4001;
}

function formatTime() {
  return new Date().toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  });
}
</script>
