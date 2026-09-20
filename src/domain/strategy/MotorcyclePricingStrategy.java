package domain.strategy;

import domain.model.Vehicle;
import java.time.Duration;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class MotorcyclePricingStrategy implements PricingStrategy {
    private static final int FREE_GRACE_PERIOD_MINUTES = 30;
    private static final double HOURLY_RATE = 10.0;

    @Override
    // [OOP: METHOD] Method calculateFee() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public double calculateFee(Duration duration, Vehicle vehicle) {
        long minutes = duration.toMinutes();
        if (minutes <= FREE_GRACE_PERIOD_MINUTES) {
            return 0.0;
        }

        long hours = (minutes + 59) / 60;
        return hours * HOURLY_RATE;
    }

    @Override
    // [OOP: METHOD] Method getStrategyName() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getStrategyName() {
        return "Motorcycle Economy Rate";
    }

    @Override
    // [OOP: METHOD] Method getRateDescription() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public String getRateDescription() {
        return "ฟรี 30 นาทีแรก | คิด 10 บาท/ชม.";
    }
}
