package domain.strategy;

import domain.model.ParkingFloor;
import domain.model.Slot;
import domain.model.Vehicle;
import java.util.List;
import java.util.Optional;

public class NearestFirstAllocationStrategy implements SlotAllocationStrategy {

    @Override
    public Optional<Slot> findSlot(List<ParkingFloor> floors, Vehicle vehicle) {
        if (floors == null || vehicle == null) {
            return Optional.empty();
        }

        for (ParkingFloor floor : floors) {
            Optional<Slot> candidate = floor.findAvailableSlotFor(vehicle);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }
}
