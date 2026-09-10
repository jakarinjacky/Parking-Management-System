package domain.payment;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
import java.time.LocalDateTime;

/**
 * Abstraction & Polymorphism: Payment
 * โครงสร้างพื้นฐานสำหรับการชำระเงินในระบบลานจอดรถ
 * รองรับการเพิ่มช่องทางชำระเงินใหม่ๆ (เช่น Wallet, บัตรสมาชิก) ตามหลัก Open-Closed Principle
 */
public abstract class Payment {
    private final String paymentId;
    private final String ticketId;
    private final double amount;
    private final PaymentMethod method;
    private final LocalDateTime paymentTime;
    protected PaymentStatus status;
    private String transactionRef;

    public Payment(String paymentId, String ticketId, double amount, PaymentMethod method) {
        this.paymentId = paymentId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.method = method;
        this.paymentTime = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
    }

    public abstract boolean processPayment();

    public String getPaymentId() {
        return paymentId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public double getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public LocalDateTime getPaymentTime() {
        return paymentTime;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    protected void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }
}
