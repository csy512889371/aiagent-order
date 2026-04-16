package com.airline.agentorder.service;

import com.airline.agentorder.enums.OrderStatus;
import com.airline.agentorder.model.Order;
import com.airline.agentorder.repository.InMemoryOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class OrderService {

    private final InMemoryOrderRepository orderRepository;

    public OrderService(InMemoryOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> listOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> queryOrder(String bookingNo, String customerName) {
        Optional<Order> optionalOrder = orderRepository.findByBookingNo(bookingNo);
        if (optionalOrder.isEmpty()) {
            return Optional.empty();
        }
        Order order = optionalOrder.get();
        if (!normalize(order.getCustomerName()).equals(normalize(customerName))) {
            return Optional.empty();
        }
        return Optional.of(order);
    }

    public String getCancelPolicyText(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return "该订单已完成退票，不能重复办理。";
        }
        if (!order.isCanCancel()) {
            return "该订单当前不允许退票，通常原因是票种限制或已超过可退票时限。";
        }
        return "该订单支持退票。退票手续费为 " + order.getCancelFee().stripTrailingZeros().toPlainString()
            + " 元。退票后订单状态将更新为已退票，且无法恢复。页面右侧聊天框将显示“确认”和“取消”按钮，请通过按钮完成确认。";
    }

    public String getChangePolicyText(Order order, String newFlightDate) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return "该订单已退票，不能再更改预订。";
        }
        if (!order.isCanChange()) {
            return "该订单当前不允许更改预订，通常原因是票种限制或已临近起飞时间。";
        }
        Optional<LocalDate> parsedDate = parseDate(newFlightDate);
        if (parsedDate.isEmpty()) {
            return "新的出发日期格式无效，请使用 YYYY-MM-DD，例如 2026-05-01。";
        }
        if (parsedDate.get().equals(order.getFlightDate())) {
            return "您提供的新日期与当前航班日期相同，请提供一个不同的改签日期。";
        }
        return "该订单支持更改预订。新的出发日期将调整为 " + newFlightDate
            + "，改签手续费为 " + order.getChangeFee().stripTrailingZeros().toPlainString()
            + " 元。改签完成后订单状态将更新为已改签。页面右侧聊天框将显示“确认”和“取消”按钮，请通过按钮完成确认。";
    }

    public CancelResult cancelOrder(String bookingNo, String customerName) {
        Optional<Order> optionalOrder = queryOrder(bookingNo, customerName);
        if (optionalOrder.isEmpty()) {
            return new CancelResult(false, "未找到匹配的订单，请核对预订号和姓名。", null);
        }

        Order order = optionalOrder.get();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return new CancelResult(false, "该订单已是退票状态，无需重复办理。", order);
        }
        if (!order.isCanCancel()) {
            return new CancelResult(false, "该订单当前不可退票，无法继续办理。", order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setLastModifiedTime(LocalDateTime.now());
        orderRepository.save(order);
        return new CancelResult(true, "退票办理成功。", order);
    }

    public ChangeResult changeOrder(String bookingNo, String customerName, String newFlightDate) {
        Optional<Order> optionalOrder = queryOrder(bookingNo, customerName);
        if (optionalOrder.isEmpty()) {
            return new ChangeResult(false, "未找到匹配的订单，请核对预订号和姓名。", null);
        }
        Optional<LocalDate> parsedDate = parseDate(newFlightDate);
        if (parsedDate.isEmpty()) {
            return new ChangeResult(false, "新的出发日期格式无效，请使用 YYYY-MM-DD，例如 2026-05-01。", null);
        }

        Order order = optionalOrder.get();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return new ChangeResult(false, "该订单已退票，无法继续更改预订。", order);
        }
        if (!order.isCanChange()) {
            return new ChangeResult(false, "该订单当前不可更改预订，无法继续办理。", order);
        }
        if (parsedDate.get().equals(order.getFlightDate())) {
            return new ChangeResult(false, "新的出发日期与当前日期相同，请更换后再试。", order);
        }

        order.setFlightDate(parsedDate.get());
        order.setStatus(OrderStatus.CHANGED);
        order.setLastModifiedTime(LocalDateTime.now());
        orderRepository.save(order);
        return new ChangeResult(true, "更改预订办理成功。", order);
    }

    private Optional<LocalDate> parseDate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text.trim()));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    public record CancelResult(boolean success, String message, Order order) {
    }

    public record ChangeResult(boolean success, String message, Order order) {
    }
}
