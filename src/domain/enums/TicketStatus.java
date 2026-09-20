package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum TicketStatus {
    ACTIVE("กำลังใช้งาน / รถจอดอยู่ในลาน"),
    PAID("ชำระเงินเรียบร้อยแล้ว"),
    EXITED("ออกจากลานจอดเรียบร้อยแล้ว"),
    LOST("ตั๋วสูญหาย");

    private final String description;

    TicketStatus(String description) {
        this.description = description;
    }

    // [OOP: METHOD] Method getDescription() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getDescription() {
        return description;
    }
}
