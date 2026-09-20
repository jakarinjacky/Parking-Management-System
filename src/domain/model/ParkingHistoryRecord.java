package domain.model;

import domain.enums.VehicleType;
import java.time.LocalDateTime;

/**
 * ประวัติการเข้าและออกของรถหนึ่งคัน ใช้เป็นข้อมูลถาวรแยกจาก Ticket ที่ทำงานในหน่วยความจำ
 */
// [OOP: CLASS] Entity สำหรับเก็บประวัติรถเข้าออกหนึ่งรอบการจอด
public class ParkingHistoryRecord {
    private final String ticketId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final int floorNumber;
    private final String slotNumber;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double fee;

    // [OOP: CONSTRUCTOR] สร้างประวัติเมื่อรถเข้าลาน
    public ParkingHistoryRecord(String ticketId, String licensePlate, VehicleType vehicleType,
                                int floorNumber, String slotNumber, LocalDateTime entryTime) {
        this(ticketId, licensePlate, vehicleType, floorNumber, slotNumber, entryTime, null, 0.0);
    }

    // [OOP: CONSTRUCTOR] ใช้คืนค่าประวัติที่อ่านจากไฟล์
    public ParkingHistoryRecord(String ticketId, String licensePlate, VehicleType vehicleType,
                                int floorNumber, String slotNumber, LocalDateTime entryTime,
                                LocalDateTime exitTime, double fee) {
        this.ticketId = ticketId;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.floorNumber = floorNumber;
        this.slotNumber = slotNumber;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.fee = fee;
    }

    // [OOP: METHOD] บันทึกเวลาออกและค่าบริการลงในประวัติเดิม
    public synchronized void markExited(LocalDateTime exitTime, double fee) {
        if (exitTime == null || exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("เวลาออกรถต้องไม่น้อยกว่าเวลาเข้า");
        }
        this.exitTime = exitTime;
        this.fee = Math.max(0.0, fee);
    }

    public String getTicketId() { return ticketId; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public int getFloorNumber() { return floorNumber; }
    public String getSlotNumber() { return slotNumber; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public double getFee() { return fee; }
    public boolean hasExited() { return exitTime != null; }
}
