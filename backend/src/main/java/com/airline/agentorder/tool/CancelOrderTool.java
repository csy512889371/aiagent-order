package com.airline.agentorder.tool;

import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.service.OrderService;
import com.airline.agentorder.service.SessionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Component
public class CancelOrderTool implements BiFunction<CancelOrderTool.CancelOrderInput, ToolContext, String> {

    private final OrderService orderService;
    private final SessionService sessionService;

    public CancelOrderTool(OrderService orderService, SessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    @Override
    public String apply(CancelOrderInput input, ToolContext toolContext) {
        ChatSession session = ToolSessionSupport.getRequiredSession(sessionService, toolContext);
        if (!session.isPendingConfirmation()) {
            return "当前还未进入退票确认阶段，请先查询订单并确认退票规则。";
        }

        String bookingNo = pickValue(input == null ? null : input.getBookingNo(), session.getExtractedBookingNo());
        String customerName = pickValue(input == null ? null : input.getCustomerName(), session.getExtractedCustomerName());
        OrderService.CancelResult result = orderService.cancelOrder(bookingNo, customerName);
        if (result.success()) {
            sessionService.clearPendingState(session);
            sessionService.markOrderListDirty(session);
            return "退票成功。订单 " + bookingNo + " 已更新为已退票状态。";
        }

        sessionService.clearPendingState(session);
        return result.message();
    }

    private String pickValue(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    public static class CancelOrderInput {
        private String bookingNo;
        private String customerName;

        public String getBookingNo() {
            return bookingNo;
        }

        public void setBookingNo(String bookingNo) {
            this.bookingNo = bookingNo;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
    }
}
