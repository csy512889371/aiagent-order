<template>
  <section class="card order-card">
    <div class="section-head">
      <div>
        <p class="eyebrow">订单概览</p>
        <h2>订单列表</h2>
      </div>
      <span class="pill">{{ orders.length }} 条</span>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>预订号</th>
            <th>姓名</th>
            <th>日期</th>
            <th>出发地</th>
            <th>目的地</th>
            <th>状态</th>
            <th>舱位</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in orders" :key="order.bookingNo">
            <td>{{ order.bookingNo }}</td>
            <td>{{ order.customerName }}</td>
            <td>{{ order.flightDate }}</td>
            <td>{{ order.fromCity }}</td>
            <td>{{ order.toCity }}</td>
            <td>
              <span class="status" :class="order.status.toLowerCase()">
                {{ statusLabel(order.status) }}
              </span>
            </td>
            <td>{{ order.cabinClass }}</td>
            <td>
              <button
                class="ghost-btn secondary-btn"
                :disabled="order.status === 'CANCELLED' || !order.canChange"
                @click="$emit('change', order)"
              >
                更改预订
              </button>
              <button
                class="ghost-btn"
                :disabled="order.status === 'CANCELLED' || !order.canCancel"
                @click="$emit('cancel', order)"
              >
                发起退票
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
defineProps({
  orders: {
    type: Array,
    default: () => []
  }
});

defineEmits(["cancel", "change"]);

function statusLabel(status) {
  if (status === "CHANGED") {
    return "已改签";
  }
  if (status === "CANCELLED") {
    return "已退票";
  }
  return "已预订";
}
</script>
