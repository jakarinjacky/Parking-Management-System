package domain.model;

import domain.enums.SlotStatus;
import domain.enums.SlotType;

/**
 * Encapsulation: คลาส Slot ควบคุมสถานะของช่องจอดรถ
 * มีการตรวจสอบความถูกต้อง (Invariants) ก่อนรับรถเข้าจอดหรือปลดปล่อยช่องจอด
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class Slot {
    private final String slotNumber;
    private final int floorNumber;
    private final SlotType slotType;
    private SlotStatus status;
    private Vehicle currentVehicle;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Slot
    public Slot(String slotNumber, int floorNumber, SlotType slotType) {
        this.slotNumber = slotNumber;
        this.floorNumber = floorNumber;
        this.slotType = slotType;
        this.status = SlotStatus.AVAILABLE;
        this.currentVehicle = null;
    }

    // [OOP: METHOD] Method isAvailable() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized boolean isAvailable() {
        return this.status == SlotStatus.AVAILABLE && this.currentVehicle == null;
    }

    /**
     * ใช้ Double Dispatch / Polymorphism:
     * ให้ Vehicle เป็นผู้ตัดสินว่าสามารถเข้าจอดในช่องประเภทนี้ได้หรือไม่
     */
    // [OOP: METHOD] Method canFitVehicle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean canFitVehicle(Vehicle vehicle) {
        if (!isAvailable() || vehicle == null) {
            return false;
        }
        return vehicle.canParkIn(this);
    }

    // [OOP: METHOD] Method assignVehicle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

    // [OOP: METHOD] Method release() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized Vehicle release() {
        Vehicle departed = this.currentVehicle;
        this.currentVehicle = null;
        this.status = SlotStatus.AVAILABLE;
        return departed;
    }

    // [OOP: METHOD] Method setStatus() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    // [OOP: METHOD] Method getSlotNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getSlotNumber() {
        return slotNumber;
    }

    // [OOP: METHOD] Method getFloorNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public int getFloorNumber() {
        return floorNumber;
    }

    // [OOP: METHOD] Method getSlotType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public SlotType getSlotType() {
        return slotType;
    }

    // [OOP: METHOD] Method getStatus() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public SlotStatus getStatus() {
        return status;
    }

    // [OOP: METHOD] Method getCurrentVehicle() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Vehicle getCurrentVehicle() {
        return currentVehicle;
    }

    @Override
    // [OOP: METHOD] Method toString() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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
