package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum PaymentStatus {
    PENDING("รอการชำระ"),
    SUCCESS("ชำระสำเร็จ"),
    FAILED("ชำระไม่สำเร็จ");

    private final String title;

    PaymentStatus(String title) {
        this.title = title;
    }

    // [OOP: METHOD] Method getTitle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getTitle() {
        return title;
    }
}
