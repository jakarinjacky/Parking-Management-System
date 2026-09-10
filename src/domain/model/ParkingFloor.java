package domain.model;

import domain.enums.SlotStatus;
import domain.enums.SlotType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ParkingFloor {
    private final int floorNumber;
    private final String floorName;
    private final List<Slot> slots;

    public ParkingFloor(int floorNumber, String floorName) {
        this.floorNumber = floorNumber;
        this.floorName = floorName;
        this.slots = new ArrayList<>();
    }

    public synchronized void addSlot(Slot slot) {
        this.slots.add(slot);
    }

    public List<Slot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public String getFloorName() {
        return floorName;
    }

    public Optional<Slot> findSlotByNumber(String slotNumber) {
        return slots.stream()
                .filter(s -> s.getSlotNumber().equalsIgnoreCase(slotNumber))
                .findFirst();
    }

    public Optional<Slot> findAvailableSlotFor(Vehicle vehicle) {
        return slots.stream()
                .filter(s -> s.canFitVehicle(vehicle))
                .findFirst();
    }

    public long getAvailableCount() {
        return slots.stream().filter(Slot::isAvailable).count();
    }

    public long getOccupiedCount() {
        return slots.stream().filter(s -> s.getStatus() == SlotStatus.OCCUPIED).count();
    }

    public long getAvailableCountByType(SlotType type) {
        return slots.stream()
                .filter(s -> s.getSlotType() == type && s.isAvailable())
                .count();
    }

    public int getTotalCount() {
        return slots.size();
    }
}
