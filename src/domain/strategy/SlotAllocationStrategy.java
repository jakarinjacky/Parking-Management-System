package domain.strategy;

import domain.model.ParkingFloor;
import domain.model.Slot;
import domain.model.Vehicle;
import java.util.List;
import java.util.Optional;

public interface SlotAllocationStrategy {
    Optional<Slot> findSlot(List<ParkingFloor> floors, Vehicle vehicle);
}
