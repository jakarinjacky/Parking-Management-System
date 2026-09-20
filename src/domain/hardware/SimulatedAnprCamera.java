package domain.hardware;

import domain.ai.AIParkingService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Adapter จำลองกล้อง ANPR สำหรับ Demo โดยใช้ AI rule-based เดิม */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class SimulatedAnprCamera implements LicensePlateReader {
    private final AIParkingService aiService;
    private final String deviceName;

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object SimulatedAnprCamera
    public SimulatedAnprCamera(AIParkingService aiService) {
        this(aiService, "ANPR-CAM-ENTRY-01");
    }

    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object SimulatedAnprCamera
    public SimulatedAnprCamera(AIParkingService aiService, String deviceName) {
        this.aiService = aiService;
        this.deviceName = deviceName;
    }

    @Override
    // [OOP: METHOD] Method readPlate() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public Map<String, Object> readPlate(String imageReferenceOrPlate) {
        Map<String, Object> result = new HashMap<>(aiService.simulateANPR(imageReferenceOrPlate));
        result.put("cameraId", deviceName);
        result.put("cameraMode", "SIMULATION");
        result.put("capturedAt", LocalDateTime.now().toString());
        result.put("cameraOnline", true);
        return result;
    }

    // [OOP: METHOD] Method getDeviceName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getDeviceName() { return deviceName; }
    // [OOP: METHOD] Method isOnline() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public boolean isOnline() { return true; }
}
