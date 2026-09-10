package domain.model;

import domain.enums.SlotStatus;
import domain.enums.SlotType;

/**
 * Encapsulation: คลาส Slot ควบคุมสถานะของช่องจอดรถ
 * มีการตรวจสอบความถูกต้อง (Invariants) ก่อนรับรถเข้าจอดหรือปลดปล่อยช่องจอด
 */
public class Slot {
    private final String slotNumber;
    private final int floorNumber;
    private final SlotType slotType;
    private SlotStatus status;
    private Vehicle currentVehicle;

    public Slot(String slotNumber, int floorNumber, SlotType slotType) {
        this.slotNumber = slotNumber;
        this.floorNumber = floorNumber;
        this.slotType = slotType;
        this.status = SlotStatus.AVAILABLE;
        this.currentVehicle = null;
    }

    public synchronized boolean isAvailable() {
        return this.status == SlotStatus.AVAILABLE && this.currentVehicle == null;
    }

    /**
     * ใช้ Double Dispatch / Polymorphism:
     * ให้ Vehicle เป็นผู้ตัดสินว่าสามารถเข้าจอดในช่องประเภทนี้ได้หรือไม่
     */
    public boolean canFitVehicle(Vehicle vehicle) {
        if (!isAvailable() || vehicle == null) {
            return false;
        }
        return vehicle.canParkIn(this);
    }

    public synchronized void assignVehicle(Vehicle vehicle) {
        if (!isAvailable()) {
            throw new IllegalStateException("ช่องจอด " + slotNumber + " ไม่ว่างสำหรับเข้าจอด");
        }
        if (!vehicle.canParkIn(this)) {
            throw new IllegalArgumentException("รถประเภท " + vehicle.getType() + " ไม่สามารถจอดในช่อง " + slotType + " ได้");
        }
        this.currentVehicle = vehicle;
        this.status = SlotStatus.OCCUPIED;
    }

    public synchronized Vehicle release() {
        Vehicle departed = this.currentVehicle;
        this.currentVehicle = null;
        this.status = SlotStatus.AVAILABLE;
        return departed;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public String getSlotNumber() {
        return slotNumber;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public SlotType getSlotType() {
        return slotType;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public Vehicle getCurrentVehicle() {
        return currentVehicle;
    }

    @Override
    public String toString() {
        return "Slot{" +
                "slotNumber='" + slotNumber + '\'' +
                ", floorNumber=" + floorNumber +
                ", slotType=" + slotType +
                ", status=" + status +
                ", vehicle=" + (currentVehicle != null ? currentVehicle.getLicensePlate() : "NONE") +
                '}';
    }
}
