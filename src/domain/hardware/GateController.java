package domain.hardware;

import java.util.Map;

/** Hardware abstraction สำหรับไม้กั้นจริง เช่น ESP32/Relay */
// [OOP: INTERFACE] อินเทอร์เฟซ: สัญญาที่กำหนดว่า class ที่นำไปใช้ต้องมี method ใดบ้าง
public interface GateController {
    // [OOP: METHOD] Method openGate() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    Map<String, Object> openGate(GateLane lane, String reason);
    // [OOP: METHOD] Method closeGate() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    Map<String, Object> closeGate(GateLane lane, String reason);
    // [OOP: METHOD] Method getState() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    GateState getState(GateLane lane);
    // [OOP: METHOD] Method getStatus() คือพฤติกรรมที่ interface กำหนดให้ class ผู้ใช้งานต้องสร้าง
    Map<String, Object> getStatus();
}
