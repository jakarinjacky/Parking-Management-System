package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.TruckPricingStrategy;

public class Truck extends Vehicle {

    public Truck(String licensePlate) {
        super(licensePlate, VehicleType.TRUCK, new TruckPricingStrategy());
    }

    @Override
    public boolean canParkIn(Slot slot) {
        return slot.getSlotType() == SlotType.LARGE || slot.getSlotType() == SlotType.VIP;
    }
}
