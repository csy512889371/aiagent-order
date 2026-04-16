<template>
  <section class="card trace-card">
    <div class="section-head">
      <div>
        <p class="eyebrow">Agent 监控</p>
        <h2>执行轨迹</h2>
      </div>
      <span class="pill">模型 {{ trace?.totalModelCalls ?? 0 }} 次</span>
    </div>

    <div class="trace-stats">
      <div class="trace-stat">
        <strong>{{ trace?.totalModelCalls ?? 0 }}</strong>
        <span>大模型调用</span>
      </div>
      <div class="trace-stat">
        <strong>{{ trace?.totalToolCalls ?? 0 }}</strong>
        <span>工具调用</span>
      </div>
      <div class="trace-stat">
        <strong>{{ trace?.totalMemoryOps ?? 0 }}</strong>
        <span>记忆操作</span>
      </div>
      <div class="trace-stat">
        <strong>{{ latestRun?.events?.length ?? 0 }}</strong>
        <span>当前轮事件</span>
      </div>
    </div>

    <div v-if="!latestRun" class="trace-empty">
      发送消息后，这里会展示当前轮 Agent 轨迹。
    </div>

    <template v-else>
      <div class="trace-run-head">
        <div>
          <p class="trace-label">当前轮状态</p>
          <strong>{{ latestRun.status }}</strong>
        </div>
        <div>
          <p class="trace-label">工具次数</p>
          <strong>{{ latestRun.toolCallCount }}</strong>
        </div>
        <div>
          <p class="trace-label">模型次数</p>
          <strong>{{ latestRun.modelCallCount }}</strong>
        </div>
        <div>
          <p class="trace-label">记忆次数</p>
          <strong>{{ latestRun.memoryOpCount }}</strong>
        </div>
      </div>

      <div class="trace-events">
        <article
          v-for="(event, index) in latestRun.events"
          :key="`${event.type}-${event.name}-${index}`"
          class="trace-event"
        >
          <div class="trace-event-head">
            <span class="trace-type" :class="event.type.toLowerCase()">{{ event.type }}</span>
            <strong>{{ event.name }}</strong>
            <span class="trace-duration">{{ event.durationMs }} ms</span>
          </div>
          <p class="trace-summary">{{ event.summary }}</p>
        </article>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  trace: {
    type: Object,
    default: null
  }
});

const latestRun = computed(() => props.trace?.runs?.[0] ?? null);
</script>
