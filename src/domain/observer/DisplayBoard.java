package domain.observer;

import domain.model.Slot;
import java.time.LocalDateTime;

public class DisplayBoard implements ParkingLotObserver {
    private final String boardId;
    private long totalAvailableSlots;
    private long totalOccupiedSlots;
    private String lastUpdatedMessage;
    private LocalDateTime lastUpdatedAt;

    public DisplayBoard(String boardId) {
        this.boardId = boardId;
        this.totalAvailableSlots = 0;
        this.totalOccupiedSlots = 0;
        this.lastUpdatedMessage = "ระบบพร้อมให้บริการ";
        this.lastUpdatedAt = LocalDateTime.now();
    }

    @Override
    public void onSlotUpdated(Slot slot) {
        this.lastUpdatedMessage = "ช่อง " + slot.getSlotNumber() + " เปลี่ยนสถานะเป็น " + slot.getStatus().getLabel();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    @Override
    public void onLotOccupancyChanged(long totalAvailable, long totalOccupied) {
        this.totalAvailableSlots = totalAvailable;
        this.totalOccupiedSlots = totalOccupied;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public String getBoardId() {
        return boardId;
    }

    public long getTotalAvailableSlots() {
        return totalAvailableSlots;
    }

    public long getTotalOccupiedSlots() {
        return totalOccupiedSlots;
    }

    public String getLastUpdatedMessage() {
        return lastUpdatedMessage;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
