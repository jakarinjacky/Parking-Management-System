package domain.model;

import domain.enums.VehicleType;
import java.time.LocalDateTime;

public class Reservation {
    private final String reservationId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final boolean requiresCharging;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private boolean cancelled;
    private boolean checkedIn;

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

    public boolean isActiveAt(LocalDateTime time) {
        return !cancelled && !checkedIn && !time.isBefore(startTime) && time.isBefore(endTime);
    }

    public void cancel() { this.cancelled = true; }
    public void markCheckedIn() { this.checkedIn = true; }
    public String getReservationId() { return reservationId; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public boolean isRequiresCharging() { return requiresCharging; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isCancelled() { return cancelled; }
    public boolean isCheckedIn() { return checkedIn; }
}
