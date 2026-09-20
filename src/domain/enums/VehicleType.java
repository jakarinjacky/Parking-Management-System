package domain.enums;

// [OOP: ENUM] Enum: ชุดค่าคงที่ที่ระบบอนุญาตให้เลือกใช้
public enum VehicleType {
    CAR("รถยนต์ทั่วไป"),
    MOTORCYCLE("รถจักรยานยนต์"),
    ELECTRIC_VEHICLE("รถยนต์ไฟฟ้า (EV)"),
    TRUCK("รถบรรทุก / รถขนาดใหญ่");

    private final String displayName;

    VehicleType(String displayName) {
        this.displayName = displayName;
    }

    // [OOP: METHOD] Method getDisplayName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getDisplayName() {
        return displayName;
    }
}
