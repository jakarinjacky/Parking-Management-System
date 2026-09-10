package domain.observer;

import domain.model.Slot;

/**
 * Observer Pattern: ParkingLotObserver
 * รับการแจ้งเตือนแบบเรียลไทม์เมื่อมีการเปลี่ยนแปลงสถานะช่องจอด
 * หรือความจุรวมของลานจอดรถ เพื่ออัปเดตป้ายไฟทางเข้า / จอมอนิเตอร์
 */
public interface ParkingLotObserver {
    void onSlotUpdated(Slot slot);
    void onLotOccupancyChanged(long totalAvailable, long totalOccupied);
}
