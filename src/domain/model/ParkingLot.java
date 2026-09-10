package domain.model;

import domain.enums.SlotStatus;
import domain.enums.SlotType;
import domain.observer.ParkingLotObserver;
import domain.strategy.NearestFirstAllocationStrategy;
import domain.strategy.SlotAllocationStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate Root: ParkingLot
 * จุดศูนย์กลางควบคุมอาคารจอดรถ จัดการชั้น (Floors) ช่องจอด (Slots)
 * ระบบผู้สังเกตการณ์ (Observer Pattern) และกลยุทธ์จัดสรรช่องจอด (Strategy Pattern)
 */
public class ParkingLot {
    private final String name;
    private final String address;
    private final List<ParkingFloor> floors;
    private final List<ParkingLotObserver> observers;
    private SlotAllocationStrategy allocationStrategy;

    public ParkingLot(String name, String address) {
        this.name = name;
        this.address = address;
        this.floors = new ArrayList<>();
        this.observers = new ArrayList<>();
        this.allocationStrategy = new NearestFirstAllocationStrategy();
    }

    public synchronized void addFloor(ParkingFloor floor) {
        this.floors.add(floor);
        notifyOccupancyChanged();
    }

    public synchronized void registerObserver(ParkingLotObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            observer.onLotOccupancyChanged(getTotalAvailable(), getTotalOccupied());
        }
    }

    public synchronized void removeObserver(ParkingLotObserver observer) {
        observers.remove(observer);
    }

    public void setAllocationStrategy(SlotAllocationStrategy allocationStrategy) {
        this.allocationStrategy = allocationStrategy;
    }

    public synchronized Slot parkVehicle(Vehicle vehicle) {
        Optional<Slot> slotOpt = allocationStrategy.findSlot(floors, vehicle);
        if (slotOpt.isEmpty()) {
            throw new IllegalStateException("ขออภัย ไม่มีช่องจอดว่างที่รองรับ " + vehicle.getType().getDisplayName());
        }

        Slot slot = slotOpt.get();
        slot.assignVehicle(vehicle);

        notifySlotUpdated(slot);
        notifyOccupancyChanged();
        return slot;
    }

    public synchronized Vehicle vacateSlot(String slotNumber) {
        for (ParkingFloor floor : floors) {
            Optional<Slot> slotOpt = floor.findSlotByNumber(slotNumber);
            if (slotOpt.isPresent()) {
                Slot slot = slotOpt.get();
                Vehicle vehicle = slot.release();
                notifySlotUpdated(slot);
                notifyOccupancyChanged();
                return vehicle;
            }
        }
        throw new IllegalArgumentException("ไม่พบช่องจอดหมายเลข " + slotNumber);
    }

    public Optional<Slot> findSlot(String slotNumber) {
        for (ParkingFloor floor : floors) {
            Optional<Slot> s = floor.findSlotByNumber(slotNumber);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    public Optional<Slot> findSlotByLicensePlate(String licensePlate) {
        for (ParkingFloor floor : floors) {
            for (Slot slot : floor.getSlots()) {
                if (slot.getCurrentVehicle() != null &&
                    slot.getCurrentVehicle().getLicensePlate().equalsIgnoreCase(licensePlate)) {
                    return Optional.of(slot);
                }
            }
        }
        return Optional.empty();
    }

    public long getTotalAvailable() {
        return floors.stream().mapToLong(ParkingFloor::getAvailableCount).sum();
    }

    public long getTotalOccupied() {
        return floors.stream().mapToLong(ParkingFloor::getOccupiedCount).sum();
    }

    public int getTotalCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getTotalCount).sum();
    }

    public List<ParkingFloor> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    private void notifySlotUpdated(Slot slot) {
        for (ParkingLotObserver observer : observers) {
            try {
                observer.onSlotUpdated(slot);
            } catch (Exception e) {
                System.err.println("Error notifying observer: " + e.getMessage());
            }
        }
    }

    private void notifyOccupancyChanged() {
        long available = getTotalAvailable();
        long occupied = getTotalOccupied();
        for (ParkingLotObserver observer : observers) {
            try {
                observer.onLotOccupancyChanged(available, occupied);
            } catch (Exception e) {
                System.err.println("Error notifying observer: " + e.getMessage());
            }
        }
    }
}
