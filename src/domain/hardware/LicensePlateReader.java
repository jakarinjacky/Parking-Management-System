package domain.hardware;

import java.util.Map;

/** Hardware abstraction สำหรับกล้อง ANPR/IP Camera */
// [OOP: INTERFACE] อินเทอร์เฟซ: สัญญาที่กำหนดว่า class ที่นำไปใช้ต้องมี method ใดบ้าง
public interface LicensePlateReader {
    // [OOP: METHOD] Method readPlate() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    Map<String, Object> readPlate(String imageReferenceOrPlate);
    // [OOP: METHOD] Method getDeviceName() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    String getDeviceName();
    // [OOP: METHOD] Method isOnline() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    boolean isOnline();
}
