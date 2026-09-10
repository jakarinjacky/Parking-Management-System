package domain.factory;

import domain.enums.SlotType;
import domain.model.Slot;

/**
 * Factory Pattern: SlotFactory
 * สร้างช่องจอดรถแต่ละประเภท พร้อมกำหนดรหัสช่องจอดตามมาตรฐาน
 */
public class SlotFactory {

    public static Slot createSlot(String slotNumber, int floorNumber, SlotType type) {
        if (slotNumber == null || slotNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("หมายเลขช่องจอดต้องไม่เป็นค่าว่าง");
        }
        return new Slot(slotNumber.trim().toUpperCase(), floorNumber, type);
    }
}
