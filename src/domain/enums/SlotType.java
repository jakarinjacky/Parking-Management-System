package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum SlotType {
    MOTORCYCLE("ช่องจอดรถจักรยานยนต์"),
    COMPACT("ช่องจอดรถขนาดกะทัดรัด"),
    STANDARD("ช่องจอดรถเก๋ง/SUV ทั่วไป"),
    LARGE("ช่องจอดรถขนาดใหญ่/รถตู้/รถบรรทุก"),
    VIP("ช่องจอด VIP"),
    EV_CHARGING("ช่องจอดพร้อมสถานีชาร์จ EV");

    private final String displayName;

    SlotType(String displayName) {
        this.displayName = displayName;
    }

    // [OOP: METHOD] Method getDisplayName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getDisplayName() {
        return displayName;
    }
}
