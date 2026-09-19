package domain.hardware;

import java.util.Map;

/** Hardware abstraction สำหรับไม้กั้นจริง เช่น ESP32/Relay */
public interface GateController {
    Map<String, Object> openGate(GateLane lane, String reason);
    Map<String, Object> closeGate(GateLane lane, String reason);
    GateState getState(GateLane lane);
    Map<String, Object> getStatus();
}
