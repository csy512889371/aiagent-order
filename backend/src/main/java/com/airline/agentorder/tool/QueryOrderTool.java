package com.airline.agentorder.tool;

import com.airline.agentorder.enums.PendingActionType;
import com.airline.agentorder.model.ChatSession;
import com.airline.agentorder.model.Order;
import com.airline.agentorder.service.OrderService;
import com.airline.agentorder.service.SessionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.BiFunction;

@Component
public class QueryOrderTool implements BiFunction<QueryOrderTool.QueryOrderInput, ToolContext, String> {

    private final OrderService orderService;
    private final SessionService sessionService;

    public QueryOrderTool(OrderService orderService, SessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    @Override
    public String apply(QueryOrderInput input, ToolContext toolContext) {
        if (input == null || isBlank(input.getBookingNo()) || isBlank(input.getCustomerName())) {
            ChatSession session = currentSession(toolContext);
            if (session.getCurrentIntent() == com.airline.agentorder.enums.IntentType.CHANGE_BOOKING) {
                sessionService.markAwaitingChangeBookingInfo(session);
            } else {
                sessionService.markAwaitingCancelBookingInfo(session);
            }
            return "缺少预订号或客户姓名，暂时不能查询订单。";
        }

        Optional<Order> optionalOrder = orderService.queryOrder(input.getBookingNo(), input.getCustomerName());
        if (optionalOrder.isEmpty()) {
            ChatSession session = currentSession(toolContext);
            session.setPendingAction(PendingActionType.WAIT_BOOKING_INFO);
            session.setPendingConfirmation(false);
            sessionService.rememberBookingInfo(session, null, null);
            return "未找到匹配订单，请确认预订号和姓名是否正确。";
        }

        Order order = optionalOrder.get();
        ChatSession session = currentSession(toolContext);
        sessionService.rememberBookingInfo(session, input.getBookingNo(), input.getCustomerName());
        return "订单查询成功：预订号 " + order.getBookingNo()
            + "，客户姓名 " + order.getCustomerName()
            + "，航班 " + order.getFlightNo()
            + "，出发日期 " + order.getFlightDate()
            + "，行程 " + order.getFromCity() + " 到 " + order.getToCity()
            + "，当前状态 " + order.getStatus() + "。";
    }

    private ChatSession currentSession(ToolContext toolContext) {
        return ToolSessionSupport.getRequiredSession(sessionService, toolContext);
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static class QueryOrderInput {
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
