package com.airline.agentorder.repository;

import com.airline.agentorder.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository {

    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    public List<Order> findAll() {
        return orderStore.values().stream()
            .sorted(Comparator.comparing(Order::getBookingNo))
            .map(this::copyOf)
            .toList();
    }

    public Optional<Order> findByBookingNo(String bookingNo) {
        Order order = orderStore.get(bookingNo);
        return Optional.ofNullable(order == null ? null : copyOf(order));
    }

    public void save(Order order) {
        orderStore.put(order.getBookingNo(), copyOf(order));
    }

    public void saveAll(List<Order> orders) {
        for (Order order : new ArrayList<>(orders)) {
            save(order);
        }
    }

    private Order copyOf(Order source) {
        Order target = new Order();
        target.setBookingNo(source.getBookingNo());
        target.setCustomerName(source.getCustomerName());
        target.setFlightNo(source.getFlightNo());
        target.setFlightDate(source.getFlightDate());
        target.setFromCity(source.getFromCity());
        target.setToCity(source.getToCity());
        target.setCabinClass(source.getCabinClass());
        target.setStatus(source.getStatus());
        target.setTicketPrice(source.getTicketPrice());
        target.setChangeFee(source.getChangeFee());
        target.setCancelFee(source.getCancelFee());
        target.setCanChange(source.isCanChange());
        target.setCanCancel(source.isCanCancel());
        target.setLastModifiedTime(source.getLastModifiedTime());
        return target;
    }
}
