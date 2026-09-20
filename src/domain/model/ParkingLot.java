package domain.model;

import domain.enums.SlotType;
import domain.observer.ParkingLotObserver;
import domain.strategy.NearestFirstAllocationStrategy;
import domain.strategy.SlotAllocationStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate Root: ParkingLot
 * จุดศูนย์กลางควบคุมอาคารจอดรถ จัดการชั้น (Floors) ช่องจอด (Slots)
 * ระบบผู้สังเกตการณ์ (Observer Pattern) และกลยุทธ์จัดสรรช่องจอด (Strategy Pattern)
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class ParkingLot {
    private final String name;
    private final String address;
    private final List<ParkingFloor> floors;
    private final List<ParkingLotObserver> observers;
    private SlotAllocationStrategy allocationStrategy;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object ParkingLot
    public ParkingLot(String name, String address) {
        this.name = name;
        this.address = address;
        this.floors = new ArrayList<>();
        this.observers = new ArrayList<>();
        this.allocationStrategy = new NearestFirstAllocationStrategy();
    }

    // [OOP: METHOD] Method addFloor() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void addFloor(ParkingFloor floor) {
        this.floors.add(floor);
        notifyOccupancyChanged();
    }

    // [OOP: METHOD] Method registerObserver() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void registerObserver(ParkingLotObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            observer.onLotOccupancyChanged(getTotalAvailable(), getTotalOccupied());
        }
    }

    // [OOP: METHOD] Method removeObserver() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void removeObserver(ParkingLotObserver observer) {
        observers.remove(observer);
    }

    // [OOP: METHOD] Method setAllocationStrategy() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void setAllocationStrategy(SlotAllocationStrategy allocationStrategy) {
        this.allocationStrategy = allocationStrategy;
    }

    // [OOP: METHOD] Method parkVehicle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

    // [OOP: METHOD] Method parkVehicle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized Slot parkVehicle(Vehicle vehicle, SlotType preferredType) {
        Optional<Slot> preferredSlot = floors.stream()
                .map(floor -> floor.findAvailableSlotFor(vehicle, preferredType))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (preferredSlot.isPresent()) {
            Slot slot = preferredSlot.get();
            slot.assignVehicle(vehicle);
            notifySlotUpdated(slot);
            notifyOccupancyChanged();
            return slot;
        }
        return parkVehicle(vehicle);
    }

    // [OOP: METHOD] Method vacateSlot() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

    // [OOP: METHOD] Method findSlot() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Optional<Slot> findSlot(String slotNumber) {
        for (ParkingFloor floor : floors) {
            Optional<Slot> s = floor.findSlotByNumber(slotNumber);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    // [OOP: METHOD] Method findSlotByLicensePlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

    // [OOP: METHOD] Method getTotalAvailable() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getTotalAvailable() {
        return floors.stream().mapToLong(ParkingFloor::getAvailableCount).sum();
    }

    // [OOP: METHOD] Method getTotalOccupied() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getTotalOccupied() {
        return floors.stream().mapToLong(ParkingFloor::getOccupiedCount).sum();
    }

    // [OOP: METHOD] Method getTotalCapacity() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public int getTotalCapacity() {
        return floors.stream().mapToInt(ParkingFloor::getTotalCount).sum();
    }

    // [OOP: METHOD] Method getFloors() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public List<ParkingFloor> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    // [OOP: METHOD] Method getName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getName() {
        return name;
    }

    // [OOP: METHOD] Method getAddress() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getAddress() {
        return address;
    }

    // [OOP: METHOD] Method notifySlotUpdated() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private void notifySlotUpdated(Slot slot) {
        for (ParkingLotObserver observer : observers) {
            try {
                observer.onSlotUpdated(slot);
            } catch (Exception e) {
                System.err.println("Error notifying observer: " + e.getMessage());
            }
        }
    }

    // [OOP: METHOD] Method notifyOccupancyChanged() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
