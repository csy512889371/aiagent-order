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
public class CancelPolicyTool implements BiFunction<CancelPolicyTool.CancelPolicyInput, ToolContext, String> {

    private final OrderService orderService;
    private final SessionService sessionService;

    public CancelPolicyTool(OrderService orderService, SessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    @Override
    public String apply(CancelPolicyInput input, ToolContext toolContext) {
        ChatSession session = ToolSessionSupport.getRequiredSession(sessionService, toolContext);
        String bookingNo = valueOrFallback(input == null ? null : input.getBookingNo(), session.getExtractedBookingNo());
        String customerName = session.getExtractedCustomerName();
        if (bookingNo == null || customerName == null) {
            sessionService.markAwaitingCancelBookingInfo(session);
            return "当前缺少订单识别信息，请先提供预订号和客户姓名。";
        }

        Optional<Order> optionalOrder = orderService.queryOrder(bookingNo, customerName);
        if (optionalOrder.isEmpty()) {
            sessionService.markAwaitingCancelBookingInfo(session);
            return "未找到匹配的订单，无法获取退票规则。";
        }

        Order order = optionalOrder.get();
        String policyText = orderService.getCancelPolicyText(order);
        if (order.isCanCancel() && order.getStatus() != com.airline.agentorder.enums.OrderStatus.CANCELLED) {
            sessionService.markAwaitingCancelConfirmation(session);
        } else {
            sessionService.clearPendingState(session);
        }
        return policyText;
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static class CancelPolicyInput {
        private String bookingNo;

        public String getBookingNo() {
            return bookingNo;
        }

        public void setBookingNo(String bookingNo) {
            this.bookingNo = bookingNo;
        }
    }
}
