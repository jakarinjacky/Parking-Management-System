package domain.payment;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
import java.util.UUID;

public class CashPayment extends Payment {
    private final double cashTendered;
    private double change;

    public CashPayment(String paymentId, String ticketId, double amount, double cashTendered) {
        super(paymentId, ticketId, amount, PaymentMethod.CASH);
        this.cashTendered = cashTendered;
        this.change = 0.0;
    }

    @Override
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

    public double getCashTendered() {
        return cashTendered;
    }

    public double getChange() {
        return change;
    }
}
