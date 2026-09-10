package domain.payment;

import domain.enums.PaymentMethod;
import domain.enums.PaymentStatus;
import java.util.UUID;

public class CreditCardPayment extends Payment {
    private final String maskedCardNumber;
    private final String cardHolderName;

    public CreditCardPayment(String paymentId, String ticketId, double amount, String rawCardNumber, String cardHolderName) {
        super(paymentId, ticketId, amount, PaymentMethod.CREDIT_CARD);
        this.cardHolderName = cardHolderName;
        this.maskedCardNumber = maskCard(rawCardNumber);
    }

    private static String maskCard(String raw) {
        if (raw == null || raw.length() < 4) {
            return "****-****-****-0000";
        }
        String cleaned = raw.replaceAll("[^0-9]", "");
        if (cleaned.length() <= 4) {
            return "**** " + cleaned;
        }
        return "****-****-****-" + cleaned.substring(cleaned.length() - 4);
    }

    @Override
    public boolean processPayment() {
        // Business logic ตรวจสอบวงเงินและเชื่อมโยง Payment Gateway
        if (getAmount() >= 0) {
            this.status = PaymentStatus.SUCCESS;
            this.setTransactionRef("CC-AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            return true;
        }
        this.status = PaymentStatus.FAILED;
        return false;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }
}
