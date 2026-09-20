package domain.model;

import domain.enums.VehicleType;
import java.time.LocalDateTime;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class Reservation {
    private final String reservationId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final boolean requiresCharging;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private boolean cancelled;
    private boolean checkedIn;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Reservation
    public Reservation(String reservationId, String licensePlate, VehicleType vehicleType,
                       boolean requiresCharging, LocalDateTime startTime, LocalDateTime endTime) {
        if (reservationId == null || licensePlate == null || vehicleType == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("ข้อมูลการจองไม่ครบถ้วน");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("เวลาสิ้นสุดต้องอยู่หลังเวลาเริ่มต้น");
        }
        this.reservationId = reservationId;
        this.licensePlate = licensePlate.trim().toUpperCase();
        this.vehicleType = vehicleType;
        this.requiresCharging = requiresCharging;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // [OOP: METHOD] Method isActiveAt() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isActiveAt(LocalDateTime time) {
        return !cancelled && !checkedIn && !time.isBefore(startTime) && time.isBefore(endTime);
    }

    // [OOP: METHOD] Method cancel() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void cancel() { this.cancelled = true; }
    // [OOP: METHOD] Method markCheckedIn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void markCheckedIn() { this.checkedIn = true; }
    // [OOP: METHOD] Method getReservationId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getReservationId() { return reservationId; }
    // [OOP: METHOD] Method getLicensePlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLicensePlate() { return licensePlate; }
    // [OOP: METHOD] Method getVehicleType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public VehicleType getVehicleType() { return vehicleType; }
    // [OOP: METHOD] Method isRequiresCharging() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isRequiresCharging() { return requiresCharging; }
    // [OOP: METHOD] Method getStartTime() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDateTime getStartTime() { return startTime; }
    // [OOP: METHOD] Method getEndTime() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDateTime getEndTime() { return endTime; }
    // [OOP: METHOD] Method isCancelled() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isCancelled() { return cancelled; }
    // [OOP: METHOD] Method isCheckedIn() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isCheckedIn() { return checkedIn; }
}
