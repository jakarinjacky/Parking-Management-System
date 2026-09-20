package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.MotorcyclePricingStrategy;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class Motorcycle extends Vehicle {

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Motorcycle
    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE, new MotorcyclePricingStrategy());
    }

    @Override
    // [OOP: METHOD] Method canParkIn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean canParkIn(Slot slot) {
        SlotType type = slot.getSlotType();
        return type == SlotType.MOTORCYCLE || type == SlotType.COMPACT || type == SlotType.VIP;
    }
}
