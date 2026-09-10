package domain.payment;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
import java.util.UUID;

public class PromptPayPayment extends Payment {
    private final String promptPayId; // e.g. Biller ID or Mobile / Citizen ID
    private final String qrPayload;

    public PromptPayPayment(String paymentId, String ticketId, double amount, String promptPayId) {
        super(paymentId, ticketId, amount, PaymentMethod.PROMPTPAY);
        this.promptPayId = promptPayId;
        this.qrPayload = "00020101021129370016A0000006770101110113" + promptPayId + "5802TH5303764540" + String.format("%.2f", amount);
    }

    @Override
    public boolean processPayment() {
        // Business logic ตรวจสอบยอดเงิน และอนุมัติการชำระผ่าน PromptPay Gateway
        if (getAmount() >= 0) {
            this.status = PaymentStatus.SUCCESS;
            this.setTransactionRef("PP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            return true;
        } else {
            this.status = PaymentStatus.FAILED;
            return false;
        }
    }

    public String getPromptPayId() {
        return promptPayId;
    }

    public String getQrPayload() {
        return qrPayload;
    }
}
