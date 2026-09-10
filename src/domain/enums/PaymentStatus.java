package domain.enums;

public enum PaymentStatus {
    PENDING("รอการชำระ"),
    SUCCESS("ชำระสำเร็จ"),
    FAILED("ชำระไม่สำเร็จ");

    private final String title;

    PaymentStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
