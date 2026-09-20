package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.TruckPricingStrategy;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class Truck extends Vehicle {

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Truck
    public Truck(String licensePlate) {
        super(licensePlate, VehicleType.TRUCK, new TruckPricingStrategy());
    }

    @Override
    // [OOP: METHOD] Method canParkIn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean canParkIn(Slot slot) {
        return slot.getSlotType() == SlotType.LARGE || slot.getSlotType() == SlotType.VIP;
    }
}
