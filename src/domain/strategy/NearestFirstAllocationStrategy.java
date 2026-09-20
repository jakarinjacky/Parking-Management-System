package domain.strategy;

import domain.model.ParkingFloor;
import domain.model.Slot;
import domain.model.Vehicle;
import java.util.List;
import java.util.Optional;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class NearestFirstAllocationStrategy implements SlotAllocationStrategy {

    @Override
    // [OOP: METHOD] Method findSlot() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
