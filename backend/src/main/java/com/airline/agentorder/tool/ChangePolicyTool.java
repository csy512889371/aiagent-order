package com.airline.agentorder.tool;

import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.model.Order;
import com.airline.agentorder.service.OrderService;
import com.airline.agentorder.service.SessionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.BiFunction;

@Component
public class ChangePolicyTool implements BiFunction<ChangePolicyTool.ChangePolicyInput, ToolContext, String> {

    private final OrderService orderService;
    private final SessionService sessionService;

    public ChangePolicyTool(OrderService orderService, SessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    @Override
    public String apply(ChangePolicyInput input, ToolContext toolContext) {
        ChatSession session = ToolSessionSupport.getRequiredSession(sessionService, toolContext);
        String bookingNo = valueOrFallback(input == null ? null : input.getBookingNo(), session.getExtractedBookingNo());
        String customerName = session.getExtractedCustomerName();
        String newFlightDate = valueOrFallback(input == null ? null : input.getNewFlightDate(), session.getRequestedChangeDate());
        if (bookingNo == null || customerName == null) {
            sessionService.markAwaitingChangeBookingInfo(session);
            return "当前缺少订单识别信息，请先提供预订号和客户姓名。";
        }
        if (newFlightDate == null) {
            return "请先提供您想改签到的新出发日期，格式例如 2026-05-01。";
        }

        Optional<Order> optionalOrder = orderService.queryOrder(bookingNo, customerName);
        if (optionalOrder.isEmpty()) {
            sessionService.markAwaitingChangeBookingInfo(session);
            return "未找到匹配的订单，无法获取更改预订规则。";
        }

        sessionService.rememberChangeDate(session, newFlightDate);
        Order order = optionalOrder.get();
        String policyText = orderService.getChangePolicyText(order, newFlightDate);
        if (order.isCanChange() && order.getStatus() != com.airline.agentorder.enums.OrderStatus.CANCELLED) {
            sessionService.markAwaitingChangeConfirmation(session);
        } else {
            sessionService.clearPendingState(session);
        }
        return policyText;
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static class ChangePolicyInput {
        private String bookingNo;
        private String newFlightDate;

        public String getBookingNo() {
            return bookingNo;
        }

        public void setBookingNo(String bookingNo) {
            this.bookingNo = bookingNo;
        }

        public String getNewFlightDate() {
            return newFlightDate;
        }

        public void setNewFlightDate(String newFlightDate) {
            this.newFlightDate = newFlightDate;
        }
    }
}
