package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.MotorcyclePricingStrategy;

public class Motorcycle extends Vehicle {

    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE, new MotorcyclePricingStrategy());
    }

    @Override
    public boolean canParkIn(Slot slot) {
        SlotType type = slot.getSlotType();
        return type == SlotType.MOTORCYCLE || type == SlotType.COMPACT;
    }
}
