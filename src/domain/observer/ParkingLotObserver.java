package domain.observer;

import domain.model.Slot;

/**
 * Observer Pattern: ParkingLotObserver
 * รับการแจ้งเตือนแบบเรียลไทม์เมื่อมีการเปลี่ยนแปลงสถานะช่องจอด
 * หรือความจุรวมของลานจอดรถ เพื่ออัปเดตป้ายไฟทางเข้า / จอมอนิเตอร์
 */
// [OOP: INTERFACE] อินเทอร์เฟซ: สัญญาที่กำหนดว่า class ที่นำไปใช้ต้องมี method ใดบ้าง
public interface ParkingLotObserver {
    // [OOP: METHOD] Method onSlotUpdated() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    void onSlotUpdated(Slot slot);
    // [OOP: METHOD] Method onLotOccupancyChanged() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    void onLotOccupancyChanged(long totalAvailable, long totalOccupied);
}
