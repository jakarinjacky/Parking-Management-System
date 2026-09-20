package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.EVPricingStrategy;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class ElectricVehicle extends Vehicle {
    private final boolean requiresCharging;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object ElectricVehicle
    public ElectricVehicle(String licensePlate, boolean requiresCharging) {
        super(licensePlate, VehicleType.ELECTRIC_VEHICLE, new EVPricingStrategy());
        this.requiresCharging = requiresCharging;
    }

    // [OOP: METHOD] Method isRequiresCharging() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isRequiresCharging() {
        return requiresCharging;
    }

    @Override
    // [OOP: METHOD] Method canParkIn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean canParkIn(Slot slot) {
        SlotType type = slot.getSlotType();
        if (requiresCharging) {
            return type == SlotType.EV_CHARGING;
        }
        return type == SlotType.EV_CHARGING || type == SlotType.STANDARD || type == SlotType.LARGE || type == SlotType.VIP;
    }
}
