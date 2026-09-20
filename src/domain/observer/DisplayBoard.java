package domain.observer;

import domain.model.Slot;
import java.time.LocalDateTime;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class DisplayBoard implements ParkingLotObserver {
    private final String boardId;
    private long totalAvailableSlots;
    private long totalOccupiedSlots;
    private String lastUpdatedMessage;
    private LocalDateTime lastUpdatedAt;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object DisplayBoard
    public DisplayBoard(String boardId) {
        this.boardId = boardId;
        this.totalAvailableSlots = 0;
        this.totalOccupiedSlots = 0;
        this.lastUpdatedMessage = "ระบบพร้อมให้บริการ";
        this.lastUpdatedAt = LocalDateTime.now();
    }

    @Override
    // [OOP: METHOD] Method onSlotUpdated() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void onSlotUpdated(Slot slot) {
        this.lastUpdatedMessage = "ช่อง " + slot.getSlotNumber() + " เปลี่ยนสถานะเป็น " + slot.getStatus().getLabel();
        this.lastUpdatedAt = LocalDateTime.now();
    }

    @Override
    // [OOP: METHOD] Method onLotOccupancyChanged() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public void onLotOccupancyChanged(long totalAvailable, long totalOccupied) {
        this.totalAvailableSlots = totalAvailable;
        this.totalOccupiedSlots = totalOccupied;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    // [OOP: METHOD] Method getBoardId() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getBoardId() {
        return boardId;
    }

    // [OOP: METHOD] Method getTotalAvailableSlots() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getTotalAvailableSlots() {
        return totalAvailableSlots;
    }

    // [OOP: METHOD] Method getTotalOccupiedSlots() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public long getTotalOccupiedSlots() {
        return totalOccupiedSlots;
    }

    // [OOP: METHOD] Method getLastUpdatedMessage() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getLastUpdatedMessage() {
        return lastUpdatedMessage;
    }

    // [OOP: METHOD] Method getLastUpdatedAt() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
