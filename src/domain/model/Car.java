package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.StandardPricingStrategy;

public class Car extends Vehicle {

    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR, new StandardPricingStrategy());
    }

    @Override
    public boolean canParkIn(Slot slot) {
        SlotType type = slot.getSlotType();
        return type == SlotType.STANDARD || type == SlotType.LARGE || type == SlotType.COMPACT;
    }
}
