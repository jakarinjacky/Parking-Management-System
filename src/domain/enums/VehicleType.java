package domain.enums;

public enum VehicleType {
    CAR("รถยนต์ทั่วไป"),
    MOTORCYCLE("รถจักรยานยนต์"),
    ELECTRIC_VEHICLE("รถยนต์ไฟฟ้า (EV)"),
    TRUCK("รถบรรทุก / รถขนาดใหญ่");

    private final String displayName;

    VehicleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
