package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class TruckPricingStrategy implements PricingStrategy {
    private static final double HOURLY_RATE = 50.0;

    @Override
    // [OOP: METHOD] Method calculateFee() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double calculateFee(Duration duration, Vehicle vehicle) {
        long minutes = duration.toMinutes();
        if (minutes <= 15) {
            return 0.0;
        }

        long hours = (minutes + 59) / 60;
        return hours * HOURLY_RATE;
    }

    @Override
    // [OOP: METHOD] Method getStrategyName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getStrategyName() {
        return "Heavy Vehicle & Truck Rate";
    }

    @Override
    // [OOP: METHOD] Method getRateDescription() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getRateDescription() {
        return "ฟรี 15 นาทีแรก | คิด 50 บาท/ชม. (ช่องจอดพิเศษขนาดใหญ่)";
    }
}
