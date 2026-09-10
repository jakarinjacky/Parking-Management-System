package domain.model;

import domain.enums.TicketStatus;
import domain.enums.VehicleType;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Domain Object: Ticket (ตั๋วจอดรถ)
 * บันทึกหลักฐานการเข้าจอด ยานพาหนะ ช่องจอด และสถานะการชำระเงิน
 */
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

    public Duration calculateDuration(LocalDateTime currentTime) {
        LocalDateTime end = (exitTime != null) ? exitTime : currentTime;
        return Duration.between(entryTime, end);
    }

    public synchronized void markPaid(double fee, String paymentId, LocalDateTime paidTime) {
        if (this.status == TicketStatus.EXITED) {
            throw new IllegalStateException("ตั๋วนี้ได้นำรถออกจากระบบไปแล้ว");
        }
        this.fee = fee;
        this.paymentId = paymentId;
        this.status = TicketStatus.PAID;
        this.exitTime = paidTime;
    }

    public synchronized void markExited(LocalDateTime exitTime) {
        if (this.status != TicketStatus.PAID) {
            throw new IllegalStateException("ต้องชำระเงินให้เรียบร้อยก่อนนำรถออกจากระบบ");
        }
        this.status = TicketStatus.EXITED;
        this.exitTime = exitTime;
    }

    public synchronized void markLost(double lostPenaltyFee) {
        this.status = TicketStatus.LOST;
        this.fee = lostPenaltyFee;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public String getSlotNumber() {
        return slotNumber;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public double getFee() {
        return fee;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
