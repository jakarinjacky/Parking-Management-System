package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum SlotStatus {
    AVAILABLE("ว่างพร้อมจอด"),
    OCCUPIED("มีรถจอดอยู่"),
    RESERVED("จองล่วงหน้า"),
    MAINTENANCE("ปิดซ่อมบำรุง");

    private final String label;

    SlotStatus(String label) {
        this.label = label;
    }

    // [OOP: METHOD] Method getLabel() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLabel() {
        return label;
    }
}
