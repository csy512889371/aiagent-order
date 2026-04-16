package com.airline.agentorder.init;

import com.airline.agentorder.enums.OrderStatus;
import com.airline.agentorder.model.Order;
import com.airline.agentorder.repository.InMemoryOrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DemoOrderDataInitializer implements CommandLineRunner {

    private final InMemoryOrderRepository orderRepository;

    public DemoOrderDataInitializer(InMemoryOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        orderRepository.saveAll(List.of(
            buildOrder("B202504160001", "张三", "MU2458", LocalDate.of(2025, 2, 12), "西安", "武汉", "ECONOMY", OrderStatus.BOOKED, "1280", "50", "180", true, true),
            buildOrder("B202504160002", "李四", "CA1821", LocalDate.of(2025, 2, 14), "南京", "北京", "ECONOMY", OrderStatus.BOOKED, "1560", "50", "220", true, true),
            buildOrder("B202504160003", "王五", "CZ3407", LocalDate.of(2025, 2, 16), "广州", "成都", "BUSINESS", OrderStatus.BOOKED, "2980", "0", "0", true, false),
            buildOrder("B202504160004", "赵六", "HU7789", LocalDate.of(2025, 2, 18), "上海", "深圳", "ECONOMY", OrderStatus.CANCELLED, "1320", "50", "200", true, true),
            buildOrder("B202504160005", "孙七", "MF8190", LocalDate.of(2025, 2, 20), "北京", "福州", "PREMIUM_ECONOMY", OrderStatus.CHANGED, "980", "30", "120", true, true)
        ));
    }

    private Order buildOrder(
        String bookingNo,
        String customerName,
        String flightNo,
        LocalDate flightDate,
        String fromCity,
        String toCity,
        String cabinClass,
        OrderStatus status,
        String ticketPrice,
        String changeFee,
        String cancelFee,
        boolean canChange,
        boolean canCancel
    ) {
        Order order = new Order();
        order.setBookingNo(bookingNo);
        order.setCustomerName(customerName);
        order.setFlightNo(flightNo);
        order.setFlightDate(flightDate);
        order.setFromCity(fromCity);
        order.setToCity(toCity);
        order.setCabinClass(cabinClass);
        order.setStatus(status);
        order.setTicketPrice(new BigDecimal(ticketPrice));
        order.setChangeFee(new BigDecimal(changeFee));
        order.setCancelFee(new BigDecimal(cancelFee));
        order.setCanChange(canChange);
        order.setCanCancel(canCancel);
        order.setLastModifiedTime(LocalDateTime.now());
        return order;
    }
}
