package domain.hardware;

import domain.ai.AIParkingService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Adapter จำลองกล้อง ANPR สำหรับ Demo โดยใช้ AI rule-based เดิม */
public class SimulatedAnprCamera implements LicensePlateReader {
    private final AIParkingService aiService;
    private final String deviceName;

    public SimulatedAnprCamera(AIParkingService aiService) {
        this(aiService, "ANPR-CAM-ENTRY-01");
    }

    public SimulatedAnprCamera(AIParkingService aiService, String deviceName) {
        this.aiService = aiService;
        this.deviceName = deviceName;
    }

    @Override
    public Map<String, Object> readPlate(String imageReferenceOrPlate) {
        Map<String, Object> result = new HashMap<>(aiService.simulateANPR(imageReferenceOrPlate));
        result.put("cameraId", deviceName);
        result.put("cameraMode", "SIMULATION");
        result.put("capturedAt", LocalDateTime.now().toString());
        result.put("cameraOnline", true);
        return result;
    }

    public String getDeviceName() { return deviceName; }
    public boolean isOnline() { return true; }
}
