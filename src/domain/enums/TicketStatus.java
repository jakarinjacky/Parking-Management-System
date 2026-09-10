package domain.enums;

public enum TicketStatus {
    ACTIVE("กำลังใช้งาน / รถจอดอยู่ในลาน"),
    PAID("ชำระเงินเรียบร้อยแล้ว"),
    EXITED("ออกจากลานจอดเรียบร้อยแล้ว"),
    LOST("ตั๋วสูญหาย");

    private final String description;

    TicketStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
