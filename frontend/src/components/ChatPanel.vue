<template>
  <section class="card chat-card">
    <div class="section-head">
      <div>
        <p class="eyebrow">AI 智能体</p>
        <h2>退票客服</h2>
      </div>
      <span class="online-dot"></span>
    </div>

    <div ref="messageListRef" class="message-list">
      <div
        v-for="(item, index) in messages"
        :key="`${item.role}-${index}-${item.time}`"
        class="message-row"
        :class="[item.role === 'user' ? 'user' : 'assistant', item.kind === 'tool' ? 'tool' : '']"
      >
        <div class="message-bubble">
          <p>{{ item.content }}</p>
          <span>{{ item.time }}</span>
        </div>
      </div>
    </div>

    <div v-if="pendingApproval" class="chat-approval">
      <p class="chat-approval-title">待人工确认</p>
      <p class="approval-copy">{{ pendingApproval.description }}</p>
      <p class="approval-copy">工具：{{ pendingApproval.toolName }}</p>
      <p class="approval-copy">参数：{{ pendingApproval.arguments }}</p>
      <div class="approval-actions">
        <button class="primary-btn" type="button" :disabled="loading" @click="$emit('approval', 'APPROVED')">
          确认
        </button>
        <button class="ghost-btn reject-btn" type="button" :disabled="loading" @click="$emit('approval', 'REJECTED')">
          取消
        </button>
      </div>
    </div>

    <form class="message-form" @submit.prevent="handleSubmit">
      <textarea
        v-model="draft"
        rows="3"
        :disabled="loading"
        placeholder="请输入您的问题，例如：我要退票，或 我要更改预订，预订号是 B202504160001，姓名是 张三"
        @keydown.enter.exact.prevent="handleSubmit"
      />
      <button class="primary-btn" type="submit" :disabled="loading || !draft.trim()">
        {{ loading ? "处理中..." : "发送" }}
      </button>
    </form>
  </section>
</template>

<script setup>
import { nextTick, ref, watch } from "vue";

defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  pendingApproval: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["send", "approval"]);
const draft = ref("");
const messageListRef = ref(null);

watch(
  () => messageListRef.value?.children.length,
  async () => {
    await nextTick();
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
    }
  }
);

function handleSubmit() {
  const value = draft.value.trim();
  if (!value) {
    return;
  }
  emit("send", value);
  draft.value = "";
}
</script>
