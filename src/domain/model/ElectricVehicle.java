package domain.model;

import domain.enums.SlotType;
import domain.enums.VehicleType;
import domain.strategy.EVPricingStrategy;

public class ElectricVehicle extends Vehicle {
    private final boolean requiresCharging;

    public ElectricVehicle(String licensePlate, boolean requiresCharging) {
        super(licensePlate, VehicleType.ELECTRIC_VEHICLE, new EVPricingStrategy());
        this.requiresCharging = requiresCharging;
    }

    public boolean isRequiresCharging() {
        return requiresCharging;
    }

    @Override
    public boolean canParkIn(Slot slot) {
        SlotType type = slot.getSlotType();
        if (requiresCharging) {
            return type == SlotType.EV_CHARGING;
        }
        return type == SlotType.EV_CHARGING || type == SlotType.STANDARD || type == SlotType.LARGE;
    }
}
