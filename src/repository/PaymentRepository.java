package repository;

import domain.enums.PaymentStatus;
import domain.payment.Payment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PaymentRepository {
    private final Map<String, Payment> paymentStorage = new ConcurrentHashMap<>();

    public synchronized void save(Payment payment) {
        paymentStorage.put(payment.getPaymentId(), payment);
    }

    public Optional<Payment> findById(String paymentId) {
        if (paymentId == null) return Optional.empty();
        return Optional.ofNullable(paymentStorage.get(paymentId.trim().toUpperCase()));
    }

    public Optional<Payment> findByTicketId(String ticketId) {
        if (ticketId == null) return Optional.empty();
        return paymentStorage.values().stream()
                .filter(p -> p.getTicketId().equalsIgnoreCase(ticketId.trim()))
                .findFirst();
    }

    public List<Payment> findAll() {
        return paymentStorage.values().stream()
                .sorted(Comparator.comparing(Payment::getPaymentTime).reversed())
                .collect(Collectors.toList());
    }

    public double getTotalRevenue() {
        return paymentStorage.values().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();
    }
}
