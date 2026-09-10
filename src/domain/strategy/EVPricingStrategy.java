package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

public class EVPricingStrategy implements PricingStrategy {
    private static final double HOURLY_RATE = 40.0; // รวมค่าไฟและหัวชาร์จ

    @Override
    public double calculateFee(Duration duration, Vehicle vehicle) {
        long minutes = duration.toMinutes();
        if (minutes <= 10) {
            return 0.0; // 10 นาทีแรกฟรี สำหรับกลับรถ
        }

        long hours = (minutes + 59) / 60;
        return hours * HOURLY_RATE;
    }

    @Override
    public String getStrategyName() {
        return "EV Fast-Charge & Parking Rate";
    }

    @Override
    public String getRateDescription() {
        return "ฟรี 10 นาทีแรก | คิด 40 บาท/ชม. (รวมค่าจุดชาร์จไฟ EV)";
    }
}
