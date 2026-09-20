package repository;

import domain.enums.PaymentStatus;
import domain.payment.Payment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class PaymentRepository {
    private final Map<String, Payment> paymentStorage = new ConcurrentHashMap<>();

    // [OOP: METHOD] Method save() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void save(Payment payment) {
        paymentStorage.put(payment.getPaymentId(), payment);
    }

    // [OOP: METHOD] Method findById() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Payment> findById(String paymentId) {
        if (paymentId == null) return Optional.empty();
        return Optional.ofNullable(paymentStorage.get(paymentId.trim().toUpperCase()));
    }

    // [OOP: METHOD] Method findByTicketId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Payment> findByTicketId(String ticketId) {
        if (ticketId == null) return Optional.empty();
        return paymentStorage.values().stream()
                .filter(p -> p.getTicketId().equalsIgnoreCase(ticketId.trim()))
                .findFirst();
    }

    // [OOP: METHOD] Method findAll() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<Payment> findAll() {
        return paymentStorage.values().stream()
                .sorted(Comparator.comparing(Payment::getPaymentTime).reversed())
                .collect(Collectors.toList());
    }

    // [OOP: METHOD] Method getTotalRevenue() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getTotalRevenue() {
        return paymentStorage.values().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();
    }
}
