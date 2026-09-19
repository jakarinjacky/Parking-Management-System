package domain.hardware;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Controller จำลองชุด ESP32/Relay สำหรับงานสาธิต */
public class SimulatedGateController implements GateController {
    private final Map<GateLane, GateState> states = new EnumMap<>(GateLane.class);

    public SimulatedGateController() {
        for (GateLane lane : GateLane.values()) states.put(lane, GateState.CLOSED);
    }

    public synchronized Map<String, Object> openGate(GateLane lane, String reason) {
        states.put(lane, GateState.OPEN);
        return commandResult(lane, "OPEN", reason);
    }

    public synchronized Map<String, Object> closeGate(GateLane lane, String reason) {
        states.put(lane, GateState.CLOSED);
        return commandResult(lane, "CLOSE", reason);
    }

    public synchronized GateState getState(GateLane lane) { return states.get(lane); }

    public synchronized Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("controller", "SIMULATED_ESP32_RELAY");
        result.put("online", true);
        result.put("entryGate", states.get(GateLane.ENTRY).name());
        result.put("exitGate", states.get(GateLane.EXIT).name());
        return result;
    }

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
