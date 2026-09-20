package domain.strategy;

import domain.model.ParkingFloor;
import domain.model.Slot;
import domain.model.Vehicle;
import java.util.List;
import java.util.Optional;

// [OOP: INTERFACE] อินเทอร์เฟซ: สัญญาที่กำหนดว่า class ที่นำไปใช้ต้องมี method ใดบ้าง
public interface SlotAllocationStrategy {
    // [OOP: METHOD] Method findSlot() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    Optional<Slot> findSlot(List<ParkingFloor> floors, Vehicle vehicle);
}
