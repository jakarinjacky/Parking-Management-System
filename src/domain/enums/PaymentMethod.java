package domain.enums;

public enum PaymentMethod {
    PROMPTPAY("Thai QR PromptPay"),
    CREDIT_CARD("บัตรเครดิต / เดบิต"),
    CASH("เงินสด");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
