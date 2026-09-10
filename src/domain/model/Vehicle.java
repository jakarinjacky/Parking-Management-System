package domain.model;

import domain.enums.VehicleType;
import domain.strategy.PricingStrategy;

/**
 * Abstraction & Polymorphism:
 * คลาสแม่ของยานพาหนะทุกประเภท กำหนดคุณสมบัติร่วมและพฤติกรรมนามธรรม
 * canParkIn(Slot slot) และ getPricingStrategy()
 */
public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
    private final PricingStrategy pricingStrategy;

    public Vehicle(String licensePlate, VehicleType type, PricingStrategy pricingStrategy) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("ป้ายทะเบียนรถต้องไม่เป็นค่าว่าง");
        }
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.type = type;
        this.pricingStrategy = pricingStrategy;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getType() {
        return type;
    }

    public PricingStrategy getPricingStrategy() {
        return pricingStrategy;
    }

    /**
     * Polymorphism: รถแต่ละชนิดตัดสินว่าสามารถจอดในช่องจอดประเภทใดได้บ้าง
     */
    public abstract boolean canParkIn(Slot slot);

    @Override
    public String toString() {
        return type + " [" + licensePlate + "]";
    }
}
