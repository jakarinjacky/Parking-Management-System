package domain.enums;

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

    public String getDisplayName() {
        return displayName;
    }
}
