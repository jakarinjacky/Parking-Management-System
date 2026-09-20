package domain.model;

import domain.enums.TicketStatus;
import domain.enums.VehicleType;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Domain Object: Ticket (ตั๋วจอดรถ)
 * บันทึกหลักฐานการเข้าจอด ยานพาหนะ ช่องจอด และสถานะการชำระเงิน
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class Ticket {
    private final String ticketId;
    private final String licensePlate;
    private final VehicleType vehicleType;
    private final int floorNumber;
    private final String slotNumber;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;
    private double fee;
    private String paymentId;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object Ticket
    public Ticket(String ticketId, String licensePlate, VehicleType vehicleType, int floorNumber, String slotNumber, LocalDateTime entryTime) {
        this.ticketId = ticketId;
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.floorNumber = floorNumber;
        this.slotNumber = slotNumber;
        this.entryTime = entryTime;
        this.status = TicketStatus.ACTIVE;
        this.fee = 0.0;
        this.paymentId = null;
    }

    // [OOP: METHOD] Method calculateDuration() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Duration calculateDuration(LocalDateTime currentTime) {
        LocalDateTime end = (exitTime != null) ? exitTime : currentTime;
        return Duration.between(entryTime, end);
    }

    // [OOP: METHOD] Method markPaid() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void markPaid(double fee, String paymentId, LocalDateTime paidTime) {
        if (this.status == TicketStatus.EXITED) {
            throw new IllegalStateException("ตั๋วนี้ได้นำรถออกจากระบบไปแล้ว");
        }
        this.fee = fee;
        this.paymentId = paymentId;
        this.status = TicketStatus.PAID;
        this.exitTime = paidTime;
    }

    // [OOP: METHOD] Method markExited() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void markExited(LocalDateTime exitTime) {
        if (this.status != TicketStatus.PAID) {
            throw new IllegalStateException("ต้องชำระเงินให้เรียบร้อยก่อนนำรถออกจากระบบ");
        }
        this.status = TicketStatus.EXITED;
        this.exitTime = exitTime;
    }

    // [OOP: METHOD] Method markLost() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized void markLost(double lostPenaltyFee) {
        this.status = TicketStatus.LOST;
        this.fee = lostPenaltyFee;
    }

    // [OOP: METHOD] Method getTicketId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getTicketId() {
        return ticketId;
    }

    // [OOP: METHOD] Method getLicensePlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLicensePlate() {
        return licensePlate;
    }

    // [OOP: METHOD] Method getVehicleType() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public VehicleType getVehicleType() {
        return vehicleType;
    }

    // [OOP: METHOD] Method getFloorNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public int getFloorNumber() {
        return floorNumber;
    }

    // [OOP: METHOD] Method getSlotNumber() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getSlotNumber() {
        return slotNumber;
    }

    // [OOP: METHOD] Method getEntryTime() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    // [OOP: METHOD] Method getExitTime() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDateTime getExitTime() {
        return exitTime;
    }

    // [OOP: METHOD] Method getStatus() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public TicketStatus getStatus() {
        return status;
    }

    // [OOP: METHOD] Method getFee() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double getFee() {
        return fee;
    }

    // [OOP: METHOD] Method getPaymentId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getPaymentId() {
        return paymentId;
    }
}
