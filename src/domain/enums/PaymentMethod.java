package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum PaymentMethod {
    PROMPTPAY("Thai QR PromptPay"),
    CREDIT_CARD("บัตรเครดิต / เดบิต"),
    CASH("เงินสด");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    // [OOP: METHOD] Method getLabel() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLabel() {
        return label;
    }
}
