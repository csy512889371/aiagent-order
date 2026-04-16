package com.airline.agentorder.tool;

import com.airline.agentorder.enums.PendingActionType;
import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.service.OrderService;
import com.airline.agentorder.service.SessionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Component
public class ChangeOrderTool implements BiFunction<ChangeOrderTool.ChangeOrderInput, ToolContext, String> {

    private final OrderService orderService;
    private final SessionService sessionService;

    public ChangeOrderTool(OrderService orderService, SessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    @Override
    public String apply(ChangeOrderInput input, ToolContext toolContext) {
        ChatSession session = ToolSessionSupport.getRequiredSession(sessionService, toolContext);
        if (session.getPendingAction() != PendingActionType.WAIT_CONFIRM_CHANGE) {
            return "当前还未进入更改预订确认阶段，请先查询订单并确认改签规则。";
        }

        String bookingNo = pickValue(input == null ? null : input.getBookingNo(), session.getExtractedBookingNo());
        String customerName = pickValue(input == null ? null : input.getCustomerName(), session.getExtractedCustomerName());
        String newFlightDate = pickValue(input == null ? null : input.getNewFlightDate(), session.getRequestedChangeDate());
        OrderService.ChangeResult result = orderService.changeOrder(bookingNo, customerName, newFlightDate);
        if (result.success()) {
            sessionService.clearPendingState(session);
            sessionService.markOrderListDirty(session);
            return "更改预订成功。订单 " + bookingNo + " 已改签到 " + newFlightDate + "。";
        }

        sessionService.clearPendingState(session);
        return result.message();
    }

    private String pickValue(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    public static class ChangeOrderInput {
        private String bookingNo;
        private String customerName;
        private String newFlightDate;

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

        public String getNewFlightDate() {
            return newFlightDate;
        }

        public void setNewFlightDate(String newFlightDate) {
            this.newFlightDate = newFlightDate;
        }
    }
}
