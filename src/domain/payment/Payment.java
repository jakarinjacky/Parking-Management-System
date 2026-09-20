package domain.payment;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
import java.time.LocalDateTime;

/**
 * Abstraction & Polymorphism: Payment
 * โครงสร้างพื้นฐานสำหรับการชำระเงินในระบบลานจอดรถ
 * รองรับการเพิ่มช่องทางชำระเงินใหม่ๆ (เช่น Wallet, บัตรสมาชิก) ตามหลัก Open-Closed Principle
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public abstract class Payment {
    private final String paymentId;
    private final String ticketId;
    private final double amount;
    private final PaymentMethod method;
    private final LocalDateTime paymentTime;
    protected PaymentStatus status;
    private String transactionRef;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Payment
    public Payment(String paymentId, String ticketId, double amount, PaymentMethod method) {
        this.paymentId = paymentId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.method = method;
        this.paymentTime = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
    }

    // [OOP: METHOD] Method processPayment() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public abstract boolean processPayment();

    // [OOP: METHOD] Method getPaymentId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getPaymentId() {
        return paymentId;
    }

    // [OOP: METHOD] Method getTicketId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getTicketId() {
        return ticketId;
    }

    // [OOP: METHOD] Method getAmount() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getAmount() {
        return amount;
    }

    // [OOP: METHOD] Method getMethod() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public PaymentMethod getMethod() {
        return method;
    }

    // [OOP: METHOD] Method getPaymentTime() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDateTime getPaymentTime() {
        return paymentTime;
    }

    // [OOP: METHOD] Method getStatus() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public PaymentStatus getStatus() {
        return status;
    }

    // [OOP: METHOD] Method getTransactionRef() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getTransactionRef() {
        return transactionRef;
    }

    // [OOP: METHOD] Method setTransactionRef() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    protected void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }
}
