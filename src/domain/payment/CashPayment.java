package domain.payment;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
import java.util.UUID;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class CashPayment extends Payment {
    private final double cashTendered;
    private double change;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object CashPayment
    public CashPayment(String paymentId, String ticketId, double amount, double cashTendered) {
        super(paymentId, ticketId, amount, PaymentMethod.CASH);
        this.cashTendered = cashTendered;
        this.change = 0.0;
    }

    @Override
    // [OOP: METHOD] Method processPayment() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean processPayment() {
        if (cashTendered < getAmount()) {
            this.status = PaymentStatus.FAILED;
            return false;
        }
        this.change = cashTendered - getAmount();
        this.status = PaymentStatus.SUCCESS;
        this.setTransactionRef("CASH-RCP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return true;
    }

    // [OOP: METHOD] Method getCashTendered() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getCashTendered() {
        return cashTendered;
    }

    // [OOP: METHOD] Method getChange() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getChange() {
        return change;
    }
}
