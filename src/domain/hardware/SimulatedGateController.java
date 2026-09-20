package domain.hardware;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Controller จำลองชุด ESP32/Relay สำหรับงานสาธิต */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class SimulatedGateController implements GateController {
    private final Map<GateLane, GateState> states = new EnumMap<>(GateLane.class);

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object SimulatedGateController
    public SimulatedGateController() {
        for (GateLane lane : GateLane.values()) states.put(lane, GateState.CLOSED);
    }

    // [OOP: METHOD] Method openGate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized Map<String, Object> openGate(GateLane lane, String reason) {
        states.put(lane, GateState.OPEN);
        return commandResult(lane, "OPEN", reason);
    }

    // [OOP: METHOD] Method closeGate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized Map<String, Object> closeGate(GateLane lane, String reason) {
        states.put(lane, GateState.CLOSED);
        return commandResult(lane, "CLOSE", reason);
    }

    // [OOP: METHOD] Method getState() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized GateState getState(GateLane lane) { return states.get(lane); }

    // [OOP: METHOD] Method getStatus() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public synchronized Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("controller", "SIMULATED_ESP32_RELAY");
        result.put("online", true);
        result.put("entryGate", states.get(GateLane.ENTRY).name());
        result.put("exitGate", states.get(GateLane.EXIT).name());
        return result;
    }

    // [OOP: METHOD] Method commandResult() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private Map<String, Object> commandResult(GateLane lane, String action, String reason) {
        Map<String, Object> result = new HashMap<>();
        result.put("controller", "SIMULATED_ESP32_RELAY");
        result.put("lane", lane.name());
        result.put("action", action);
        result.put("state", states.get(lane).name());
        result.put("reason", reason == null ? "" : reason);
        result.put("commandedAt", LocalDateTime.now().toString());
        result.put("autoCloseAfterSeconds", action.equals("OPEN") ? 3 : 0);
        return result;
    }
}
