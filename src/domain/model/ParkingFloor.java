package domain.model;

import domain.enums.SlotStatus;
import domain.enums.SlotType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class ParkingFloor {
    private final int floorNumber;
    private final String floorName;
    private final List<Slot> slots;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object ParkingFloor
    public ParkingFloor(int floorNumber, String floorName) {
        this.floorNumber = floorNumber;
        this.floorName = floorName;
        this.slots = new ArrayList<>();
    }

    // [OOP: METHOD] Method addSlot() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void addSlot(Slot slot) {
        this.slots.add(slot);
    }

    // [OOP: METHOD] Method getSlots() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<Slot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    // [OOP: METHOD] Method getFloorNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public int getFloorNumber() {
        return floorNumber;
    }

    // [OOP: METHOD] Method getFloorName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getFloorName() {
        return floorName;
    }

    // [OOP: METHOD] Method findSlotByNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Slot> findSlotByNumber(String slotNumber) {
        return slots.stream()
                .filter(s -> s.getSlotNumber().equalsIgnoreCase(slotNumber))
                .findFirst();
    }

    // [OOP: METHOD] Method findAvailableSlotFor() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Slot> findAvailableSlotFor(Vehicle vehicle) {
        return slots.stream()
                .filter(s -> s.canFitVehicle(vehicle))
                .findFirst();
    }

    // [OOP: METHOD] Method findAvailableSlotFor() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Slot> findAvailableSlotFor(Vehicle vehicle, SlotType preferredType) {
        return slots.stream()
                .filter(s -> s.getSlotType() == preferredType && s.canFitVehicle(vehicle))
                .findFirst();
    }

    // [OOP: METHOD] Method getAvailableCount() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getAvailableCount() {
        return slots.stream().filter(Slot::isAvailable).count();
    }

    // [OOP: METHOD] Method getOccupiedCount() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getOccupiedCount() {
        return slots.stream().filter(s -> s.getStatus() == SlotStatus.OCCUPIED).count();
    }

    // [OOP: METHOD] Method getAvailableCountByType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getAvailableCountByType(SlotType type) {
        return slots.stream()
                .filter(s -> s.getSlotType() == type && s.isAvailable())
                .count();
    }

    // [OOP: METHOD] Method getTotalCount() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public int getTotalCount() {
        return slots.size();
    }
}
