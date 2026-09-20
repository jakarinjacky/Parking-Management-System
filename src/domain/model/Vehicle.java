package domain.model;

import domain.enums.VehicleType;
import domain.strategy.PricingStrategy;

/**
 * Abstraction & Polymorphism:
 * คลาสแม่ของยานพาหนะทุกประเภท กำหนดคุณสมบัติร่วมและพฤติกรรมนามธรรม
 * canParkIn(Slot slot) และ getPricingStrategy()
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
    private final PricingStrategy pricingStrategy;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Vehicle
    public Vehicle(String licensePlate, VehicleType type, PricingStrategy pricingStrategy) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("ป้ายทะเบียนรถต้องไม่เป็นค่าว่าง");
        }
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.type = type;
        this.pricingStrategy = pricingStrategy;
    }

    // [OOP: METHOD] Method getLicensePlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLicensePlate() {
        return licensePlate;
    }

    // [OOP: METHOD] Method getType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public VehicleType getType() {
        return type;
    }

    // [OOP: METHOD] Method getPricingStrategy() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public PricingStrategy getPricingStrategy() {
        return pricingStrategy;
    }

    /**
     * Polymorphism: รถแต่ละชนิดตัดสินว่าสามารถจอดในช่องจอดประเภทใดได้บ้าง
     */
    // [OOP: METHOD] Method canParkIn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public abstract boolean canParkIn(Slot slot);

    @Override
    // [OOP: METHOD] Method toString() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String toString() {
        return type + " [" + licensePlate + "]";
    }
}
