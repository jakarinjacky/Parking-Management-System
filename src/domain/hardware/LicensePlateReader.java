package domain.hardware;

import java.util.Map;

/** Hardware abstraction สำหรับกล้อง ANPR/IP Camera */
public interface LicensePlateReader {
    Map<String, Object> readPlate(String imageReferenceOrPlate);
    String getDeviceName();
    boolean isOnline();
}
